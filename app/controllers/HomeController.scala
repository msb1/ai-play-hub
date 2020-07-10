package controllers

import actors.KafkaConsumerClass.{InitConsumer, RunConsumer}
import actors.KafkaProducerClass.InitProducer
import actors.{KafkaConsumerActor, KafkaProducerActor}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import akka.{Done, NotUsed}
import ch.qos.logback.classic.{Level, Logger}
import javax.inject.Inject
import models.{AuthResponse, FormMessage, Role, User}
import org.mindrot.jbcrypt.BCrypt
import org.slf4j
import org.slf4j.LoggerFactory
import play.api.http.websocket._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import repositories.UserRepository
import security.{AuthAction, AuthService}
import utils.{BrokerConfig, JsonUtil, KafkaConfig}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Success, Try}


case class NumRecords(var dataEvents: Long, var modelEvents: Long, var processedEvents: Long)

class HomeController @Inject()(cc: ControllerComponents, config: play.api.Configuration, authAction: AuthAction)
                              (implicit system: ActorSystem, mat: Materializer, ec: ExecutionContext) extends AbstractController(cc) {

  // set org, akka and kafka Logger levels to WARN (to avoid excess verbosity)
  LoggerFactory.getLogger("org").asInstanceOf[Logger].setLevel(Level.WARN)
  LoggerFactory.getLogger("akka").asInstanceOf[Logger].setLevel(Level.WARN)
  LoggerFactory.getLogger("kafka").asInstanceOf[Logger].setLevel(Level.WARN)

  // start play logger (logback with slf4j)
  val logger: slf4j.Logger = play.api.Logger(getClass).logger

  // Create actors for Kafka Producer and EPD Simulator
  logger.info("Start Kafka Producer Stream")
  val kafkaProducer: ActorRef = system.actorOf(Props[KafkaProducerActor], "kafkaProducer")
  val kafkaConsumer: ActorRef = system.actorOf(Props(new KafkaConsumerActor(kafkaProducer)), "kafkaConsumer")

  // Read in simulator configuration
  val filePath = "/home/bw/scala/Misc/kafkaTest/"
  logger.info("Kafka Akka Test program...")

  // Create hubSink and hubSource for feeding websocket - messages all go to runwith(hubSink)
  val (hubSink, hubSource) =
    MergeHub.source[String].toMat(BroadcastHub.sink[String])(Keep.both).run()
  // defaults and initial values
  var numRecords: NumRecords = NumRecords(0L, 0L, 0L)

  // Create messageSink for messages received from websockets where message is two parts separated by ';'
  // Each message has a header and a body
  val messageSink: Sink[String, Future[Done]] = Sink.foreach { wsMessage =>
    // logger.info(s"Message received by Sink: $wsMessage")
    val msg = wsMessage.split(";")
    if (msg(0) == "controlMessage") {
      msg(1).trim match {

        case "PONG" =>
          Source.single("params;PING").delay(60 seconds).runWith(hubSink)
          logger.info("PONG message received from Websockets...")

        case _ =>
          logger.info("Unrecognizable message received from Websockets...")
      }
    }
  }

  // add default users for starting application
  val user1: User = User("barnwaldo", "1234", "Barn", "Waldo", "barnwaldo@gmail.com", enabled = true, Array(Role.ADMIN.toString, Role.USER.toString))
  val user2: User = User("jrchuck", "5678", "Johnson", "Rahbeck", "jrchuck@yahoo.com", enabled = true, Array(Role.ADMIN.toString))
  val user3: User = User("nice", "asdf", "Nic", "Eps", "nickei@msn.com", enabled = true, Array(Role.USER.toString))
  // delete all users from database - TESTING
  UserRepository.deleteAll().transform(
    tryResult => Success(
      tryResult.fold(th => logger.error(s"No users deleted from database... Error Message: " + th.getMessage),
        option => logger.info(s"Users deleted from database... ${option.toString} entries"))))

  // insert three TEST users into database
  UserRepository.insertUser(user1).transform(
    tryResult => Success(
      tryResult.fold(th => logger.error(s"User ${user1.username} could not be inserted into database... Error Message: " + th.getMessage),
        _ => logger.info(s"User ${user1.username} is registered - TESTING..."))))

  UserRepository.insertUser(user2).transform(
    tryResult => Success(
      tryResult.fold(th => logger.error(s"User ${user2.username} could not be inserted into database... Error Message: " + th.getMessage),
        _ => logger.info(s"User ${user2.username} is registered - TESTING..."))))

  UserRepository.insertUser(user3).transform(
    tryResult => Success(
      tryResult.fold(th => logger.error(s"User ${user3.username} could not be inserted into database... Error Message: " + th.getMessage),
        _ => logger.info(s"User ${user3.username} is registered - TESTING..."))))

  // login start view makes initial connection here
  def init: Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    logger.info("INITIALIZE connection between React and Play...")
    Ok(JsonUtil.toString(FormMessage("Init Play response to React...")))
  }

  // login is routed to this async method
  def login: Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    if (request.body.asJson.isEmpty) {
      Future {
        BadRequest(JsonUtil.toString(FormMessage("Login user is not registered...")))
      }
    } else {
      val message: JsValue = request.body.asJson.get
      val username: String = Json.stringify((message \ "username").get).replaceAll("^\"|\"$", "")
      val password: String = Json.stringify((message \ "password").get).replaceAll("^\"|\"$", "")
      logger.info(s"login -- username: $username  password: $password")
      val userFuture: Future[Option[User]] = UserRepository.findUser(username)
      // utilize transform/fold but could use than map/recover with Future
      userFuture.transform(
        tryResult =>
          Success(
            // Note fold operation -- def fold[U](fa: Throwable => U, fb: T => U): U
            tryResult.fold(th => BadRequest(s"User $username find in database failed... Error Message: " + th.getMessage),
              userOption => {
                if (userOption.isEmpty) {
                  logger.info(s"Bad login request attempted... Login user: $username is not registered...")
                  BadRequest(JsonUtil.toString(FormMessage("Login user is not registered...")))
                } else {
                  val user = userOption.get
                  logger.info(s"User $user found in database...")
                  if (BCrypt.checkpw(password, user.password)) {
                    logger.info(s"User $username is logged in...")
                    // start kafka producer/consumer and websockets
                    // initialize kafka producer and consumer
                    numRecords = NumRecords(0L, 0L, 0L)
                    kafkaProducer ! InitProducer(numRecords, hubSink)
                    kafkaConsumer ! InitConsumer(numRecords, hubSink)
                    kafkaConsumer ! RunConsumer
                    // generate JWT token and send to front end in response
                    val token = AuthService.generateToken(User.getUserDetails(user))
                    Ok(JsonUtil.toString(AuthResponse(username, token)))
                  }
                  else {
                    logger.info("User password does not match...")
                    BadRequest(JsonUtil.toString(FormMessage("User password does not match...")))
                  }
                }
              })
          )
      )
    }
  }

  // signout is routed to this method
  def signout(): Action[AnyContent] = authAction { implicit request: Request[AnyContent] =>
    if (request.body.asJson.isEmpty) {
      logger.info("Logout/signout error - incomplete content from front end...")
      BadRequest("Logout/signout error - incomplete content from front end...")
    } else {
      val message: JsValue = request.body.asJson.get
      val token: String = Json.stringify((message \ "content").get).replaceAll("^\"|\"$", "")
      val signoutMessage: String = s"Inside signout with token: $token -- User ${AuthService.getUsernameFromToken(token)} is logged out"
      logger.info(signoutMessage)
      Ok(s"User ${AuthService.getUsernameFromToken(token)} is logged out")
    }
  }

  def register(): Action[AnyContent] = Action.async { implicit request: Request[AnyContent] =>
    val messageBody = request.body.asJson
    logger.info("Inside register..." + messageBody.get)
    if (messageBody.isEmpty) {
      logger.info("Bad registration request...")
      Future {
        BadRequest(JsonUtil.toString(FormMessage("Bad registration request...")))
      }
    } else {
      val message: JsValue = request.body.asJson.get
      val username: String = Json.stringify((message \ "username").get).replaceAll("^\"|\"$", "")
      val password: String = Json.stringify((message \ "password").get).replaceAll("^\"|\"$", "")
      val firstname: String = Json.stringify((message \ "firstname").get).replaceAll("^\"|\"$", "")
      val lastname: String = Json.stringify((message \ "lastname").get).replaceAll("^\"|\"$", "")
      val email: String = Json.stringify((message \ "email").get).replaceAll("^\"|\"$", "")
      val enabled: Boolean = Try(Json.stringify((message \ "enabled").get).replaceAll("^\"|\"$", "").toBoolean).getOrElse(false)
      val user = User(username, password, firstname, lastname, email, enabled, Array(Role.ADMIN.toString))
      UserRepository.insertUser(user).transform(
        tryResult => Success(
          tryResult.fold(
            th => {
              logger.error(s"User ${user.username} could not be inserted into database... Error Message: " + th.getMessage)
              BadRequest(JsonUtil.toString(FormMessage("Bad registration request...")))
            },
            _ => {
              logger.info(s"User ${user.username} is registered...")
              Ok(s"User ${user.username} is registered...")
            })))
    }
  }

  def getBrokerConfig: Action[AnyContent] = authAction {
    implicit request: Request[AnyContent] =>
      val brokerConfig = BrokerConfig(KafkaConfig.hubname, KafkaConfig.bootstrapServers, KafkaConfig.modelKey,
        KafkaConfig.dataTopic, KafkaConfig.modelTopic, KafkaConfig.processedTopic)
      Ok(JsonUtil.toString(brokerConfig))
  }

  def setBrokerConfig(): Action[AnyContent] = authAction {
    implicit request: Request[AnyContent] =>
      val messageBody = request.body.asJson
      if (messageBody.isEmpty) {
        logger.info("setBrokerConfig() has failed to update parameters...")
        BadRequest(JsonUtil.toString(FormMessage("setBrokerConfig() has failed to update parameters...")))
      } else {
        val message: JsValue = request.body.asJson.get
        KafkaConfig.modelTopic = Json.stringify((message \ "modelTopic").get).replaceAll("^\"|\"$", "")
        KafkaConfig.modelKey = Json.stringify((message \ "modelKey").get).replaceAll("^\"|\"$", "")
        Ok(JsonUtil.toString(FormMessage("Broker Config has been set...")))
      }
  }

  implicit val transformer: WebSocket.MessageFlowTransformer[Message, Message] = WebSocket.MessageFlowTransformer.identityMessageFlowTransformer
  val userFlow: Flow[Message, Message, NotUsed] =
    Flow[Message].mapAsync(1) {
      case TextMessage(text) => Future.successful(text)
      case BinaryMessage(data) => Future.successful(data.utf8String)
      case PingMessage(data) => Future.successful(data.utf8String)
      case PongMessage(data) => Future.successful(data.utf8String)
      case CloseMessage(statusCode, reason) => Future.successful(s"${
        statusCode.getOrElse(-1)
      } -- Websocket closed: $reason")
    }.via(Flow.fromSinkAndSource(messageSink, hubSource))
      .map[Message](string => TextMessage(string))

  // Basic Play WebSockets based on userFlow - no origin check
  // def ws: WebSocket = WebSocket.accept[Message, Message] { request =>
  //   userFlow
  // }

  // Play WebSockets based on userFlow - origin check enabled
  def ws: WebSocket = WebSocket.acceptOrResult[Message, Message] {
    request => Future.successful(request.headers.get("Origin") match {

        case Some(originValue) if originMatches(originValue) =>
          logger.info(s"originCheck: originValue = $originValue")
          sendMessagetoWebSocket(s"messaging;originCheck: originValue = $originValue", hubSink)
          Right(userFlow)

        case Some(badOrigin) =>
          logger.error(s"originCheck: rejecting request because Origin header value $badOrigin is not in the same origin")
          sendMessagetoWebSocket("messaging;*** ORIGIN CHECK FAILED ***", hubSink)
          Left(Forbidden)

        case None =>
          logger.error("originCheck: rejecting request because no Origin header found")
          sendMessagetoWebSocket("messaging;*** ORIGIN HEADER NOT FOUND - FAILED ***", hubSink)
          Left(Forbidden)
      })
  }

  def sendMessagetoWebSocket(msg: String, hubSink: Sink[String, NotUsed]): Unit = {
    Source.single(msg).runWith(hubSink)
  }

  // Returns true if the value of the Origin header contains an acceptable value.
  // Production done through configuration same as the allowedhosts filter.
  def originMatches(origin: String): Boolean = {
    origin.contains("localhost:3000") || origin.contains("localhost:13001")
  }
}

