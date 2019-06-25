import IndexProcedures._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, blocking}

object Main {

  var ocrParser: OCRParser = _
  var nlpParser: NLPParser = _
  var esIndexer: ESIndexer = _
  var s3Downloader: S3Downloader = _
  var argMap: Map[String, String] = _

  def main(args: Array[String]) {

    argMap = {

      if (args.contains("--help") || args.contains("--h")) printHelp()

      @tailrec
      def mapFromArgsIter(argList: Array[String], argMap: Map[String, String]): Map[String, String] = argList match {
        case Array() => argMap
        case Array(h, _*) =>
          val Array(k, v) = h.toLowerCase.split(":")

          mapFromArgsIter(argList.tail, argMap + (k -> v))
      }

      val defaults = Map[String, String](
        "esurl" -> "http://localhost:9200",
        "endurl" -> "limit=100",
        "studyid" -> "",
        "do" -> "adminremote",
        "localinput" -> "./input/",
        "bucket" -> "TEMP"
      )

      mapFromArgsIter(args, defaults)
    }

    val startTime = System.currentTimeMillis()

    val f2 = Future {
      if (argMap("do").equals("adminremote")) {
        blocking {
          indexPDFRemote("https://kf-api-dataservice.kids-first.io", "/genomic-files", argMap("endurl"))
        }
      } else {
        indexPDFLocal(argMap("localinput"))
      }
    }.flatten
    val f1 = Future {
      blocking {
        indexParticipants("https://kf-api-dataservice.kids-first.io", "/participants", argMap("endurl"))
      }
    }.flatten


    val f = Future.sequence(Seq(f1, f2))
    Await.result(f, Duration.Inf)

    println("took " + (System.currentTimeMillis() - startTime) / 1000 + " seconds")
    System.exit(0)
  }

  def printHelp(): Unit = {
    val help =
      """
        |HELP: command line arguments are of form key:value and are not case sensitive
        |Here are the possible options:
        |- esurl : ElasticSearch UR
        |- starturl : first portion of the URL on which we iterate to get the file's S3 keys
        |- midurl : middle portion (changes while iterating)
        |- endurl : end portion (additionnal options for the GET request). Must fit all requests (example: limit=100 is ok, but not file_format=something)
        |- do : what the program will do (adminremote to index remote files, adminlocal to index local files)
        |- localinput : the local folder used as source for adminlocal
        |- bucket : the S3 source bucket
        |As an example of the syntax, to change the localinput to MYFOLDER, one would write "localinput:MYFOLDER"
      """.stripMargin
    println(help)
    System.exit(0)
  }

}