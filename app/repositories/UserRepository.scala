package repositories

import akka.actor.ActorSystem
import ch.qos.logback.classic.{Level, Logger}
import com.mongodb.BasicDBObject
import com.typesafe.config.{Config, ConfigFactory}
import models.User
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mindrot.jbcrypt.BCrypt
import org.mongodb.scala._
import org.mongodb.scala.bson.codecs.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros._
import org.mongodb.scala.model.Filters
import org.mongodb.scala.result.DeleteResult
import org.slf4j.LoggerFactory

import scala.concurrent.Future

object UserRepository {

  // set Logger levels to WARN (to avoid excess verbosity)
  LoggerFactory.getLogger("org").asInstanceOf[Logger].setLevel(Level.WARN)
  LoggerFactory.getLogger("akka").asInstanceOf[Logger].setLevel(Level.WARN)
  // start play logger (logback with slf4j)
  val logger = play.api.Logger(getClass).logger

  // define ActorSystem and Materializer for akka streams
  implicit val actorSystem = ActorSystem("Play-Hub")
  private val config: Config = ConfigFactory.load()

  // establish MongoDB connection
  private val codecRegistry = fromRegistries(fromProviders(classOf[User]), DEFAULT_CODEC_REGISTRY)
  private val client: MongoClient = MongoClient(config.getString("mongodb.uri"))
  private val db: MongoDatabase = client.getDatabase(config.getString("mongodb.db")).withCodecRegistry(codecRegistry)
  private val userscoll: MongoCollection[User] = db.getCollection("users")
  logger.info(config.getString("mongodb.uri") + " -- " + config.getString("mongodb.db"))


  def findUser(username: String): Future[Option[User]] = {
    userscoll.find(Filters.eq("username", username.toString))
      .first().
      toFutureOption()
  }

  def findAll(): Future[Option[Seq[User]]] = {
    userscoll.find().collect()
      .toFutureOption()
  }

  def insertUser(user: User): Future[Option[Completed]] = {
    val updatedUser = user.copy(password = BCrypt.hashpw(user.password, BCrypt.gensalt()))
    userscoll.insertOne(updatedUser).toFutureOption()
  }

  def deleteUser(username: String): Unit = {
    val obs: Observable[DeleteResult] = userscoll.deleteOne(Filters.equal("username", username))
    obs.subscribe(new Observer[DeleteResult] {
      override def onNext(result: DeleteResult): Unit = logger.info(s"User $username deleted from database... ${result.toString} entries")
      override def onError(e: Throwable): Unit = logger.error(s"User $username could not be deleted from database...")
      override def onComplete(): Unit = logger.info(s"User $username database delete is complete...")
    })
  }

  def deleteAll(): Future[Option[DeleteResult]] = {
    userscoll.deleteMany(new BasicDBObject()).toFutureOption()
  }

}