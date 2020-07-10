package actors

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorSystem}
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.{BroadcastHub, Keep, Sink, Source}
import com.typesafe.config.Config
import controllers.NumRecords
import models.EpdData
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.Logger
import utils.JsonUtil

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

class KafkaProducerActor extends Actor {

  import KafkaProducerClass._

  // define ActorSystem for akka streams
  implicit val system: ActorSystem = ActorSystem("Kafka")
  val logger: Logger = play.api.Logger(getClass).logger

  // kafka producer config/settings
  val producerConfig: Config = system.settings.config.getConfig("akka.kafka.producer")
  var producerSettings: ProducerSettings[String, String] = ProducerSettings(producerConfig, new StringSerializer, new StringSerializer)
  var messageSink: Option[Sink[String, NotUsed]] = None: Option[Sink[String, NotUsed]]
  var numRecords: Option[NumRecords] = None: Option[NumRecords]
  val defaultSink: Sink[Any, Future[Done]] = Sink.ignore
  val defaultNumRecords: NumRecords = NumRecords(0L, 0L, 0L)

  var producerMessages = new ArrayBuffer[String](5)

  def receive: Receive = {

    case InitProducer(records: NumRecords, hubSink: Sink[String, NotUsed]) =>
      numRecords = Some(records)
      messageSink = Some(hubSink)

    case ProducerMessage(topic, key, value) =>
      // logger.info(s"--> $topic  $key  $value")
      val webSocketSink = messageSink.getOrElse(defaultSink)

      val messageSource: Source[ProducerRecord[String, String], NotUsed] =
        Source.single(ProducerMessage(topic, key, value)).map(msg => {
          new ProducerRecord[String, String](topic, 0, msg.key, msg.value)
        }).toMat(BroadcastHub.sink(bufferSize = 1))(Keep.right).run()

      // send Producer messages to Kafka Producer
      messageSource.runWith(Producer.plainSink(producerSettings))
      // send Producer messages to hubSink to websocket
      messageSource.map(msg => "producer;" + updateProducerMessages(msg.value()) )
        .runWith(webSocketSink)
      // send message summary status to websocket
      messageSource.map(msg => {
        val num = numRecords.getOrElse(defaultNumRecords)
        val dataRecord: EpdData = JsonUtil.toType[EpdData](msg.value)
        if(dataRecord.result == 1) {
          num.modelEvents += 1
        }
        "status;" + JsonUtil.toString(num)
      }).runWith(webSocketSink)
      // output data records to logger
      // messageSource.map(msg => logger.info(msg.key + " -- " + msg.value)).runWith(Sink.ignore)

    case TerminateProducer =>
      system.terminate()
      println("System terminate for Kafka Producer...")
    case _ => println("KafkaProducer received something unexpected... No action taken...")
  }

  def updateProducerMessages(msg: String): String = {
    val processedMsg: String = msg.substring(1, msg.length() - 1).replaceAll(",", ", ")
    producerMessages += processedMsg
    if (producerMessages.length > 5) {
      producerMessages.remove(0)
    }
    val sb: StringBuilder = new StringBuilder()
    producerMessages.foreach(s =>
      sb.append("<p>").append(s).append("</p>")
    )
    sb.toString()
  }
}

object KafkaProducerClass {

  case class InitProducer(records: NumRecords, hubSink: Sink[String, NotUsed])

  case class ProducerMessage(topic: String, key: String, value: String)

  case object TerminateProducer
}