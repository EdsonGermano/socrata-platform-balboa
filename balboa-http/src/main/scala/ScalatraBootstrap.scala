import javax.servlet.ServletContext

import com.codahale.metrics.JmxReporter
import com.socrata.balboa.metrics.data.DefaultDataStoreFactory
import com.socrata.balboa.server._
import com.socrata.balboa.util.LoggingConfigurator
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import org.scalatra.LifeCycle
import org.scalatra.metrics.MetricsBootstrap

class ScalatraBootstrap extends LifeCycle with MetricsBootstrap with StrictLogging {
  /**
    * The reporter determines the destination of the metrics. Additionally, the
    * reporter determines units that the values are reported in. In this case,
    * metrics are made available as JMX properties, where our collectd
    * configuration will gather them and transmit them back to Graphite. Dig
    * into the JmxReporter source code for the units that are used.
    */
  val jmxReporter = JmxReporter.forRegistry(metricRegistry).build()

  override def init(context: ServletContext): Unit = {
    jmxReporter.start()

    LoggingConfigurator.configureLogging(ConfigFactory.load())

    logger.info("Assigning servlet handlers.")
    val dataStoreFactory = DefaultDataStoreFactory

    context.mount(new VersionServlet(), "/version")
    context.mount(new EntitiesServlet(dataStoreFactory), "/entities")
    context.mount(new MetricsServlet(dataStoreFactory), "/metrics")
    context.mount(new HealthCheckServlet(dataStoreFactory), "/health")
    context.mount(new NotFoundServlet, "/")
  }

  override def destroy(context: ServletContext): Unit = {
    super.destroy(context)
    jmxReporter.close()
  }
}
