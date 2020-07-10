package models

import java.io.File

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.immutable._
import scala.collection.mutable.ArrayBuffer

case class Continuous(id: Int, name: String, max: Double, min: Double, dist: String, pos1: Double, pos2: Double, neg1: Double, neg2: Double)

case class Discrete(id: Int, name: String, num: Int, levels: String, pos: String, neg: String)

case class Alert(id: Int, name: String, atype: String, warn: Double, alarm: Double, incr: Boolean)

case class EpdConfig(name: String, err: Double, success: Double, alrt: List[Alert], cont: List[Continuous], disc: List[Discrete])

case class EquipConfig(fileName: String, taktTime: Int, offsetDelay: Int, consumerTopic: String, producerTopic: String)

case class EpdData(uuid: String, currentTime: String, topic: String, categories: HashMap[String, Int],
                   sensors: HashMap[String, Double], result: Int)

case class DataResult(key: String, uuid: String, recordTime: String, prediction: Int)

object DataRecord {

  val logger: Logger = LoggerFactory.getLogger("DataRecord")

  def readConfigData(equip: HashMap[String, EquipConfig]): Array[EpdConfig] = {

    var epdConfig = ArrayBuffer[EpdConfig]()

    for ((_, value) <- equip) {
      // read config files
      logger.info(s"Reading ${value.fileName} ...")
      val yamlFile = new File(value.fileName)
      val objectMapper = new ObjectMapper(new YAMLFactory()) with ScalaObjectMapper
      objectMapper.registerModule(DefaultScalaModule)
      epdConfig += objectMapper.readValue[EpdConfig](yamlFile, classOf[EpdConfig])
    }
    epdConfig.foreach({
      println
    })
    epdConfig.toArray
  }

  def readMasterConfig(equipFileName: String): HashMap[String, EquipConfig] = {
    // read equip config master file
    logger.info(s"Reading $equipFileName ...")
    val yamlFile = new File(equipFileName)

    val objectMapper = new ObjectMapper(new YAMLFactory()) with ScalaObjectMapper
    objectMapper.registerModule(DefaultScalaModule)
    val equipConfig: HashMap[String, EquipConfig] = objectMapper
      .readValue[HashMap[String, EquipConfig]](yamlFile, classOf[HashMap[String, EquipConfig]])
    println(equipConfig)
    equipConfig
  }

}

