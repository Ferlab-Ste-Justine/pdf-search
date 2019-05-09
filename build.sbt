name := "clin-pdf-indexer"

version := "0.1"

scalaVersion := "2.12.8"

//libraryDependencies += "org.overviewproject" %% "pdfocr" % "0.0.10"

libraryDependencies += "net.sourceforge.tess4j" % "tess4j" % "4.0.0"

// https://mvnrepository.com/artifact/io.searchbox/jest-common
libraryDependencies += "io.searchbox" % "jest-common" % "0.1.1"

libraryDependencies += "org.apache.opennlp" % "opennlp-tools" % "1.9.1"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test"

// https://mvnrepository.com/artifact/org.elasticsearch.client/elasticsearch-rest-high-level-client
libraryDependencies += "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "7.0.1"