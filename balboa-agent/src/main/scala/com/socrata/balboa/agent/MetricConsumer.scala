package com.socrata.balboa.agent

import java.io._
import java.util.regex.Pattern

import com.codahale.metrics.Timer
import com.socrata.balboa.agent.metrics.BalboaAgentMetrics
import com.socrata.balboa.util.FileUtils
import com.socrata.metrics.{Fluff, MetricQueue}
import com.typesafe.scalalogging.LazyLogging
import scodec.Attempt.{Successful, Failure => AttemptFailure}
import scodec.bits.BitVector
import scodec.{Codec, DecodeResult}

import scala.util.{Failure, Success, Try}

/**
  * The MetricConsumer consumes metrics from data files from within a specific directory.  Any metrics extracted will
  * be pushed to a specified [[com.socrata.metrics.MetricQueue]].
  *
  * <br>
  *
  * Given a root directory, the MetricConsumer will recursively extract all the directories under the root directory.
  * For each directory, files are processed in reverse alphabetical order excluding the last file in the order.  It is
  * common and suggested that metric producers use timestamps in their file names and not place any additional files
  * within the same directory they write metrics to.
  *
  */
//  TODO: Make this contract better (assuming last file off-limits is dumb)

object MetricConsumer {
  val TIMESTAMP: String = "timestamp"
  val ENTITY_ID: String = "entityId"
  val NAME: String = "name"
  val VALUE: String = "value"
  val RECORD_TYPE: String = "type"
  val fields: List[String] = List(TIMESTAMP, ENTITY_ID, NAME, VALUE, RECORD_TYPE)
  val integerPattern: Pattern = "-?[0-9]+".r.pattern
}

/**
  * Creates a MetricConsumer that processes files from `directory` and emits them to `metricPublisher`.  The `fileProvider`
  * is a control mechanism that allows clients to make determinations on which [[java.io.File]]s can be processed.
  *
  * @param directory       Directory in which to process metrics.
  * @param metricPublisher Queue to emit metrics to.
  * @param fileProvider    The { @link FileFilter} used to determine which files are allowed to be processed.
  */
class MetricConsumer(val directory: File, val metricPublisher: MetricQueue, val fileProvider: MetricFileProvider)
  extends Runnable with AutoCloseable with LazyLogging {

  if (directory == null || !directory.isDirectory) throw new IllegalArgumentException("Illegal Data directory " + directory)
  if (metricPublisher == null) throw new NullPointerException("Metric Queue cannot be null")

  /**
    * Creates Metric consumer that will attempt to find all the metric data within a directory
    * and push them to a metricPublisher. Does not take responsibility for closing the metricPublisher.
    */
  def this(directory: File, metricPublisher: MetricQueue) {
    this(directory, metricPublisher, AlphabeticMetricFileProvider(directory.toPath))
  }

  /**
    * Attempts to process all the sub directories on the root directories for all possible
    * metrics.
    */
  def run() {
    logger.info(s"Looking for metrics files recursively in '${this.directory.getAbsoluteFile}'")
    val runTimer: Timer.Context = BalboaAgentMetrics.totalRuntime.time
    val start: Long = System.currentTimeMillis
    var recordsProcessed: Int = 0
    // Treat each individual Metric data file as its own isolated run.
    // We are trying to prevent the failure to process one file from blocking or preventing the processing
    // of others.
    val files: Set[File] = fileProvider.provide
    files.foreach { metricsEventLog: File =>
      logger.info(s"Processing '${metricsEventLog.getAbsolutePath}'.")
      val maybeRecords: Try[List[MetricsRecord]] = Try(processFile(metricsEventLog))
      maybeRecords match {
        case Failure(e: IOException) =>
          logger.error(s"Error reading records from $metricsEventLog", e)
          BalboaAgentMetrics.metricsProcessingFailureCounter.inc()
          val broken: File = new File(metricsEventLog.getAbsolutePath + FileUtils.BROKEN_FILE_EXTENSION)
          if (!metricsEventLog.renameTo(broken)) {
            logger.warn(s"Unable to rename broken file $metricsEventLog permissions issue?")
            BalboaAgentMetrics.renameBrokenFileFailureCounter.inc()
          }
        case Success(records) =>
          records.foreach { r =>
            metricPublisher.create(Fluff(r.entityId), Fluff(r.name), r.value.longValue, r.timestamp, r.metricType)
          }
          recordsProcessed += records.size
          if (!metricsEventLog.delete) {
            logger.error(s"Unable to delete event log $metricsEventLog - file may be read twice, which is bad.")
            BalboaAgentMetrics.deleteEventFailureCounter.inc()
          }
        case _ =>
      }
    }
    val processingTime: Long = System.currentTimeMillis - start
    logger.info(s"Run completed, processed $recordsProcessed in $processingTime ms")
    BalboaAgentMetrics.metricsEmittedCount.inc(recordsProcessed)
    BalboaAgentMetrics.metricsEmittedMeter.mark(recordsProcessed)
    runTimer.stop
  }

  /**
    * This method does nothing. As the MetricConsumer does not take unique
    * ownership of its metricPublisher, it cannot be sure that it is safe to close.
    */
  @throws[Exception]
  def close() {
  }

  /**
    * Given a metrics data file, attempt to extract all the metrics from the file and
    * pushes these metrics into the underlying metricPublisher.
    *
    * @param f File to process.
    * @return A list of { @link MetricsRecord}s that were process.
    * @throws IOException When there is a problem processing the file.
    */
  @throws[IOException]
  private def processFile(f: File): List[MetricsRecord] = {
    val filePath: String = f.getAbsolutePath
    logger.info(s"Processing file $filePath")

    val bitVector = BitVector.fromMmap(new FileInputStream(f).getChannel)
    val recordsAttempt = Codec
      .decodeCollect[Vector, MetricsRecord](MetricsRecord.codec.asDecoder, None)(bitVector)
    val results: Vector[MetricsRecord] = recordsAttempt match {
      case Successful(DecodeResult(value, remainder)) =>
        if (remainder.nonEmpty) {
          logger.warn(s"Metric records file $filePath had remaining bits after decoding; is probably incomplete")
          Vector.empty
        } else {
          value
        }
      case AttemptFailure(cause) =>
        logger.error(s"Error decoding metric records: ${cause.messageWithContext}")
        Vector.empty
    }

    results.toList
  }

  override def toString: String = s"MetricConsumer{directory=$directory, metricPublisher=$metricPublisher}"
}
