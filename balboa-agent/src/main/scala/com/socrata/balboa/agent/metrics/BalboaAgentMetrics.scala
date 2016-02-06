package com.socrata.balboa.agent.metrics

import java.io.{FileFilter, File}
import java.util.concurrent.TimeUnit

import com.codahale.metrics._
import com.socrata.balboa.util.FileUtils

import scala.util.{Failure, Success, Try}

/**
  * Balboa Agent Metrics inventory.  The metric inventory
  *
  * Created by michaelhotan on 1/28/16.
  */
object BalboaAgentMetrics {

  /**
    * NOTE: This also serves as an example of how to use Dropwizard and surface metrics via a Dropwizard provided
    * reporter.  IE. See [[com.socrata.balboa.agent.BalboaAgent.jmxReporter]] to see how to surface metrics via JMX.
    */

  private val name = "balboa-agent"

  /**
    * Single Metrics registry for Balboa Agent.
    */
  val registry: MetricRegistry = new MetricRegistry

  /**
    * Count of Metrics emitted.
    */
  val metricsEmittedCount: Counter = registry.counter(MetricRegistry.name(name, "metrics", "emitted-count"))

  /**
    * A meter of how many metrics are being emitted
    */
  val metricsEmittedMeter: Meter = registry.meter(MetricRegistry.name(name, "metrics", "emitted"))

  /**
    * Record how long a single Metric Consume job will take.
    * Provide a rate of consume jobs per second.
    * NOTE: consume jobs reads all the files within a metrics directory.
    */
  val totalRuntime: Timer = registry.timer(MetricRegistry.name(name, "total", "runtime"))

  /**
    * Histogram distribution of per metrics directory runtimes (ms)
    */
  val singleDirectoryRuntimeHistogram: Histogram = registry.histogram(MetricRegistry.name(name, "directory", "runtime"))

  /**
    * Histogram distribution of per metrics directory number of metric records processed.
    */
  val singleDirectoryNumProcessedHistogram: Histogram = registry.histogram(MetricRegistry.name(name, "directory", "numprocessed"))

  // NOTE: Substantial Errors that should not be ignored and be made fully aware.
  /**
    * Records the number of times a metrics processing error is found.
    */
  val metricsProcessingFailureCounter: Counter = registry.counter(MetricRegistry.name(name, "error", "metrics-processing-failure"))

  /**
    * Records the number of times renaming a broken files failed.
    * NOTE: When an error occurs while processing a file then the file will attempted to be archived with a broken suffix.
    */
  val renameBrokenFileFailureCounter: Counter = registry.counter(MetricRegistry.name(name, "error", "rename-broken-file-failure"))

  /**
    * Records the count of the number of times the consumer failed to delete a complete metric log file.
    */
  val deleteEventFailureCounter: Counter = registry.counter(MetricRegistry.name(name, "error", "delete-event-log-failure"))

  /**
    * Records the count of the number of times an incomplete field was encountered when parsing our ridiculously well
    * thought through custom data format.
    */
  val incompleteFieldCounter: Counter = registry.counter(MetricRegistry.name(name, "error", "incomplete-field"))

  /**
    * Records the count of the number of times an incorrect value was encountered in the value field of the metric
    */
  val errorInvalidValueCounter: Counter = registry.counter(MetricRegistry.name(name, "error", "invalid-value"))

  /**
    * Creates a metric that counts a directory for files within that directory
    *
    * @param metricName The name of the metric to create.
    * @param directory The directory to count metrics in.
    * @param filter Filter to use to count files.  If none returns all files
    * @return Success(Metric) if successfully create the metric, False otherwise
    */
  def numFiles(metricName: String, directory: File, filter: Option[FileFilter]): Try[Metric] = directory match {
    case f: File if directory.exists() && directory.isDirectory =>
      Success(registry.register(MetricRegistry.name(name, metricName, "num", "files"),
        new CachedGauge[Int](5, TimeUnit.MINUTES) { //
        override def loadValue(): Int = FileUtils.getDirectories(directory).map(dir =>
            filter match {
              case Some(f1) => dir.listFiles(f1).length
              case None => dir.listFiles().length
            }
          ).sum
        })
      )
    case f: File if !directory.exists() => Failure(new IllegalArgumentException(
      s"${directory.getAbsolutePath} does not exist.")
    )
    case f: File if !directory.isDirectory => Failure(new IllegalArgumentException(
      s"${directory.getAbsolutePath} is not a directory.")
    )
    case f => Failure(throw new IllegalArgumentException(s"$f is not an accessible directory."))
  }

}
