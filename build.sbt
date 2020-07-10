name := """play-hub"""
organization := "com.toptech"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.11"

libraryDependencies += "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6"
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.6.6"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.6.6"
libraryDependencies += "com.typesafe.akka" %% "akka-stream-typed" % "2.6.6"
libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.6.6"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3" % Test

// play dependencies (guice, ws)
libraryDependencies += guice
libraryDependencies += ws

// mongodb scala
libraryDependencies += "org.mongodb.scala" %% "mongo-scala-bson" % "2.9.0"
libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "2.9.0"
libraryDependencies += "org.mongodb" % "mongodb-driver-core" % "3.12.5"
libraryDependencies += "org.mongodb" % "bson" % "3.12.5"
libraryDependencies += "org.mongodb" % "mongodb-driver-async" % "3.12.5"

// Jackson json
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.11.1"
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.11.1"
libraryDependencies += "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.11.1"


// JWT
libraryDependencies += "io.jsonwebtoken" % "jjwt-api" % "0.11.0"
libraryDependencies += "io.jsonwebtoken" % "jjwt-impl" % "0.11.0"
libraryDependencies += "io.jsonwebtoken" % "jjwt-jackson" % "0.11.0"
libraryDependencies += "org.mindrot" % "jbcrypt" % "0.4"

// kafka dependencies
libraryDependencies += "org.apache.kafka" % "kafka-clients" % "2.5.0"
libraryDependencies += "org.apache.kafka" % "kafka-streams" % "2.5.0"
libraryDependencies += "org.apache.kafka" %% "kafka-streams-scala" % "2.5.0"
libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "2.0.3"

// test dependencies
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.8"
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.6.3" % Test
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % "2.6.3" % Test
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test
libraryDependencies += "org.awaitility" % "awaitility" % "4.0.1" % Test
libraryDependencies += "org.apache.kafka" % "kafka-streams-test-utils" % "2.3.1" % Test

// sbt run configuration dev settings
PlayKeys.devSettings := Seq("play.server.http.port" -> "8080")
// PlayKeys.devSettings += "play.server.websocket.frame.maxLength" -> "64k"

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-Xfatal-warnings"
)

