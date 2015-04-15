package com.socrata.balboa.impl

import com.socrata.balboa.config.ClientType.ClientType
import com.socrata.balboa.config.{ClientType, DispatcherConfig}
import com.socrata.metrics.collection.LinkedBlockingPreBufferQueue
import com.socrata.metrics.components._
import org.slf4j.LoggerFactory

/**
 * Metric Logger that internally uses a dispatcher for sending metrics
 * to multiple endpoints.
 */
trait MetricLoggerToDispatcher extends BaseMetricLoggerComponent {
  private val Log = LoggerFactory.getLogger(classOf[MetricLoggerToDispatcher])

  /**
   * NOTE: This method is being replaced with [[MetricLogger()]].
   */
  @deprecated
  override def MetricLogger(serverName: String, queueName: String, backupFileName: String): MetricLogger = MetricLogger()

  /**
   * Creates a Metric Logger using preset configurations.
   *
   * @return [[MetricLoggerLike]] instance.
   */
  override def MetricLogger(): MetricLogger = new MetricLogger() with MetricEnqueuer
    with MetricDequeuerService
    with HashMapBufferComponent
    with BalboaDispatcherComponent
    with LinkedBlockingPreBufferQueue
    with BufferedStreamEmergencyWriterComponent
    with DispatcherInformation {

    /**
     * See [[DispatcherInformation.components]].
     */
    override lazy val components: Iterable[MessageQueueComponent] = DispatcherConfig.clientTypes.map(ctype => component
      (ctype))
  }

  /**
   * Creates a component based off of its configuration type
   *
   * @param ctype Client Type to find Queue Component for.
   * @return The Queue Component for a specific client
   */
  private def component(ctype: ClientType): MessageQueueComponent = ctype match {
    case ClientType.jms =>
      new ActiveMQueueComponent()
        with ConfiguredServerInformation
        with BufferedStreamEmergencyWriterComponent
    case ClientType.kafka =>
      new BalboaKafkaComponent()
        with ConfiguredKafkaProducerInfo
        with BufferedStreamEmergencyWriterComponent
    case x =>
      throw new IllegalStateException(s"Unsupported Queue Component $x")
  }

}