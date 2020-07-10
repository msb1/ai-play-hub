package utils

import com.typesafe.config.{Config, ConfigFactory}

object KafkaConfig {

  val config: Config = ConfigFactory.load()
  // parameters that can be changed during app operation
  var dataTopic: String = config.getString("akka.kafka.consumer.data.topic")
  var modelTopic: String = config.getString("akka.kafka.producer.model.topic")
  var processedTopic: String = config.getString("akka.kafka.consumer.processed.topic")
  var modelKey: String = config.getString("akka.kafka.producer.model.key")
  // producer parameters that are only set in application.conf file
  val acks: String = config.getString("akka.kafka.producer.kafka-clients.acks")
  val bootstrapServers: String = config.getString("akka.kafka.producer.kafka-clients.bootstrap.servers")
  val clientId: String = config.getString("akka.kafka.producer.kafka-clients.client.id")
  val keySerializer: String = config.getString("akka.kafka.producer.kafka-clients.key.serializer")
  val valueSerializer: String = config.getString("akka.kafka.producer.kafka-clients.value.serializer")
  val maxInFlight: String = config.getString("akka.kafka.producer.kafka-clients.max.in.flight.requests.per.connection")
  val hubname: String = config.getString("akka.kafka.producer.hubname")
  // consumer parameters that are only set in application.conf file
  val autoCommitInterval: String = config.getString("akka.kafka.consumer.kafka-clients.auto.commit.interval.ms")
  val autoOffsetReset: String = config.getString("akka.kafka.consumer.kafka-clients.auto.offset.reset")
  val enableAutoCommit: String = config.getString("akka.kafka.consumer.kafka-clients.enable.auto.commit")
  val groupId: String = config.getString("akka.kafka.consumer.kafka-clients.group.id")
  val keyDeserializer: String = config.getString("akka.kafka.consumer.kafka-clients.key.deserializer")
  val valueDeserializer: String = config.getString("akka.kafka.consumer.kafka-clients.value.deserializer")

  def getTopicSet: Set[String] = { Set(dataTopic, processedTopic) }
}

case class BrokerConfig(hubname: String, bootstrapServers: String, modelKey: String,
                        dataTopic: String,
                        modelTopic: String,
                        processedTopic: String )