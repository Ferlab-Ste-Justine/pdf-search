name := "clin-wordcloud"

version := "0.1"

scalaVersion := "2.12.8"

val akkaVersion = "2.5.19"
val akkaHttpVersion = "10.1.3"
val circeVersion = "0.9.3"

libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,

    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,
    "de.heikoseeberger" %% "akka-http-circe" % "1.21.0",

    "org.scalatest" %% "scalatest" % "3.0.5" % Test
)

// https://mvnrepository.com/artifact/org.elasticsearch.client/elasticsearch-rest-high-level-client
libraryDependencies += "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "7.0.1"

libraryDependencies += "ch.megard" %% "akka-http-cors" % "0.4.0"