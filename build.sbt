name := "kafka-prometheus-reporter"

version := "0.1"

scalaVersion := "2.12.8"

val kafkaVersion = "2.3.0"

libraryDependencies ++= Seq(
  "org.apache.kafka" % "kafka-clients" % kafkaVersion,
  "org.apache.kafka" %% "kafka" % kafkaVersion,
  "io.prometheus" % "simpleclient" % "0.6.0",
  "io.prometheus" % "simpleclient_common" % "0.6.0",
  "com.typesafe.akka" %% "akka-http"   % "10.1.8",
  "com.typesafe.akka" %% "akka-stream" % "2.5.19",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
)
