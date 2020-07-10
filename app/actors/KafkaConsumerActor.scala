package actors

import actors.KafkaProducerClass.ProducerMessage
import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.kafka.scaladsl.Consumer
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, RunnableGraph, Sink}
import akka.{Done, NotUsed}
import com.typesafe.config.{Config, ConfigFactory}
import controllers.NumRecords
import models.{DataResult, EpdData}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import utils.{JsonUtil, KafkaConfig}

import scala.collection.{immutable, mutable}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

class KafkaConsumerActor(producer: ActorRef) extends Actor {

  import KafkaConsumerClass._

  // define ActorSystem for akka streams
  implicit val system: ActorSystem = ActorSystem("Kafka")
  val logger: Logger = play.api.Logger(getClass).logger

  // config for akka Kafka Consumer
  val consumerConfig: Config = system.settings.config.getConfig("akka.kafka.consumer")
  var consumerSettings: ConsumerSettings[String, String] = ConsumerSettings(consumerConfig, new StringDeserializer, new StringDeserializer)
  val config: Config = ConfigFactory.load()
  var consumerRunning = false

  var messageSink: Option[Sink[String, NotUsed]] = None: Option[Sink[String, NotUsed]]
  var numRecords: Option[NumRecords] = None: Option[NumRecords]
  val defaultSink: Sink[Any, Future[Done]] = Sink.ignore
  val defaultNumRecords: NumRecords = NumRecords(0L, 0L, 0L)

  var sensorTimePts: ArrayBuffer[String] = new ArrayBuffer[String](50)
  var consumerMessages: ArrayBuffer[String] = new ArrayBuffer[String](5)
  var sensorDataLists: mutable.HashMap[String, ArrayBuffer[Double]] = mutable.HashMap.empty[String, ArrayBuffer[Double]]
  val maxSensorPtsToPlot: Integer = 50

  def receive: Receive = {

    case InitConsumer(records, hubSink) =>
      numRecords = Some(records)
      messageSink = Some(hubSink)

    case RunConsumer =>
      if (!consumerRunning) {
        val consumerGraph = GraphDSL.create() { implicit builder =>
          import GraphDSL.Implicits._
          val webSocketSink = messageSink.getOrElse(defaultSink)
          logger.info(s"KafkaConsumerActor RunConsumer -> data topic: ${KafkaConfig.dataTopic} -- consumer topic: ${KafkaConfig.processedTopic}")
          // Kafka consumer to akka reactive stream
          val controlSource = Consumer.plainSource(consumerSettings, Subscriptions.topics(KafkaConfig.getTopicSet))
          // Create printSink to logger (or console)
          val printSink = Sink.foreach[ConsumerRecord[String, String]](rec => {
            logger.info(s"Consumer: topic = ${rec.topic}, partition = ${rec.partition}, offset = ${rec.offset}, key = ${rec.key} -- ${rec.value}")
          })
          // Convert consumer messages processed data to strings to be sent to websocket
          val messageConvert = Flow[ConsumerRecord[String, String]].filter(_.topic == KafkaConfig.processedTopic)
            .map(rec => "consumer;" + updateConsumerMessages(rec.value()))
          // Stream processed message summary status with each consumer message received to websocket (filter data messages)
          val statusConvert = Flow[ConsumerRecord[String, String]]
            .map(record => {
              val num = numRecords.getOrElse(defaultNumRecords)
              if (record.topic == KafkaConfig.dataTopic) num.dataEvents += 1
              else if (record.topic == KafkaConfig.processedTopic) {
                val dataResult: DataResult = JsonUtil.toType[DataResult](record.value)
                if (dataResult.prediction == 1) {
                  num.processedEvents += 1
                }
              }
              "status;" + JsonUtil.toString(num)
            })
          // Stream with data messages only (filter out consumer messages to websocket)
          val dataToProducer = Flow[ConsumerRecord[String, String]].filter(_.topic == KafkaConfig.dataTopic)
            .map(record => {
              val epdData: EpdData = JsonUtil.toType(record.value())
              // send msg to Producer for processing
              producer ! ProducerMessage(epdData.topic, epdData.topic, record.value())
              // output EpdData to next stage in flow (splitter)
              JsonUtil.toType[EpdData](record.value())
            })

          val timeDataConvert = Flow[EpdData].map(record => {
            sensorTimePts += record.currentTime
            if (sensorTimePts.length > maxSensorPtsToPlot) {
              sensorTimePts.remove(0)
            }
            "time;" + JsonUtil.objectMapper.writeValueAsString(sensorTimePts)
          })

          val sensorDataConvert = Flow[EpdData].map(record => {
            val sensors: immutable.HashMap[String, Double] = record.sensors
            sensors.foreach(rec => {
              if (!sensorDataLists.contains(rec._1)) {
                sensorDataLists += (rec._1 -> new ArrayBuffer[Double](50))
              }
              sensorDataLists(rec._1) += sensors(rec._1)

              if (sensorDataLists(rec._1).length > maxSensorPtsToPlot) {
                sensorDataLists(rec._1).remove(0)
              }
            })
            "sensors;" + JsonUtil.objectMapper.writeValueAsString(sensorDataLists)
          })

          val catDataConvert = Flow[EpdData].map(record => {
            "cats;" + JsonUtil.objectMapper.writeValueAsString(record.categories)
          })

          // use broadcaster to branch out reactive streams to multiple sinks
          val broadcaster = builder.add(Broadcast[ConsumerRecord[String, String]](4))
          val splitter = builder.add(Broadcast[EpdData](3))
          controlSource ~> broadcaster.in
          broadcaster.out(0) ~> printSink
          broadcaster.out(1) ~> messageConvert ~> webSocketSink
          broadcaster.out(2) ~> statusConvert ~> webSocketSink
          broadcaster.out(3) ~> dataToProducer ~> splitter.in
          splitter.out(0) ~> timeDataConvert ~> webSocketSink
          splitter.out(1) ~> sensorDataConvert ~> webSocketSink
          splitter.out(2) ~> catDataConvert ~> webSocketSink
          ClosedShape
        }
        RunnableGraph.fromGraph(consumerGraph).run
        consumerRunning = true
      }
    case TerminateConsumer =>
      system.terminate()
      logger.info("System terminate for Kafka Consumer Actor...")
    case _ => println("KafkaConsumerActor received something unexpected... No action taken...")
  }

  def updateConsumerMessages(msg: String): String = {
    val processedMsg: String = msg.substring(1, msg.length() - 1).replaceAll(",", ", ")
    consumerMessages += processedMsg
    if (consumerMessages.length > 5) {
      consumerMessages.remove(0)
    }
    val sb: StringBuilder = new StringBuilder()
    consumerMessages.foreach(s =>
      sb.append("<p>").append(s).append("</p>")
    )
    sb.toString()
  }
}

object KafkaConsumerClass {

  case class InitConsumer(records: NumRecords, hubSink: Sink[String, NotUsed])

  case object RunConsumer

  case object TerminateConsumer

}