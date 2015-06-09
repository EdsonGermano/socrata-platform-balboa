package com.socrata.balboa.config

import com.socrata.balboa.common.logging.BalboaLogging
import com.socrata.balboa.config.ClientType.ClientType
import com.socrata.balboa.metrics.config.{Configuration, Keys}

import scala.collection.JavaConverters._

/**
 * All inclusive Configuration file
 */
object DispatcherConfig extends BalboaLogging {

  /**
   * @return Client Types parsed from configuration.
   */
  def clientTypes: Seq[ClientType] = {
    val clientStrings: Seq[String] = Configuration.get().getList(Keys.DISPATCHER_CLIENT_TYPES).asScala

    // Notify that we found an invalid Client Type with a graceful log message
    clientStrings.filterNot(isClientType) match {
      case a: Seq[String] if a.nonEmpty => logger.warn(s"$a not a valid client type, acceptable client " +
        s"types ${ClientType.values}")
      case _ => // Success No weird Client Types.
    }

    clientStrings.filter(isClientType).map(ClientType.withName)
  }

  private def isClientType(s: String): Boolean = ClientType.values.exists(t => t.toString.equals(s))
}


