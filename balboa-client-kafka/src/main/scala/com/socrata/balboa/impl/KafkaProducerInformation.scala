package com.socrata.balboa.impl

import java.io.File

import com.socrata.balboa.config.KafkaClientConfig
import com.socrata.balboa.metrics.util.AddressAndPort
import com.socrata.metrics.config.EmergencyFileParameter

/**
 * Information that pertains to how to setup and configure producers that will communicate with Kafka.
 */
trait KafkaProducerInformation extends EmergencyFileParameter {

  /**
   * @return List of Kafka Brokers that exists within this environment.
   */
  def brokers: List[AddressAndPort]

  /**
   * @return The name of the topic that is being used.
   */
  def topic: String

}

trait ConfiguredKafkaProducerInfo extends KafkaProducerInformation {
  override lazy val brokers = KafkaClientConfig.brokers.toList
  override lazy val topic = KafkaClientConfig.topic
  override lazy val file: File = KafkaClientConfig.emergencyBackUpFile("kafka")
}