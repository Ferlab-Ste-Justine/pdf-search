name := "pdf-search"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies += "net.sourceforge.tess4j" % "tess4j" % "4.0.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"

// https://mvnrepository.com/artifact/org.elasticsearch.client/elasticsearch-rest-high-level-client
libraryDependencies += "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "6.5.0"

// https://mvnrepository.com/artifact/com.amazonaws/aws-java-sdk-s3
libraryDependencies += "com.amazonaws" % "aws-java-sdk-s3" % "1.11.549"

//https://www.playframework.com/documentation/2.7.x/ScalaJson
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.7.0"
libraryDependencies += "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.1.0-M3"
libraryDependencies += "com.typesafe.play" %% "play-ws-standalone-json" % "2.1.0-M3"

libraryDependencies += "org.apache.pdfbox" % "jbig2-imageio" % "3.0.2"
libraryDependencies += "org.apache.pdfbox" % "pdfbox" % "2.0.16"

libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value