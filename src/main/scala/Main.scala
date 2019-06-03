import java.io.{File, FileInputStream, InputStream}

import Tasker._
import IndexProcedures._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

object Main {

  var ocrParser: OCRParser = _
  var nlpParser: NLPParser = _
  var esIndexer: ESIndexer = _
  var s3Downloader: S3Downloader = _
  var argMap: Map[String, String] = _

  def printHelp(): Unit = {
    val help =
      """
        |HELP: command line arguments are of form key:value and are not case sensitive
        |Here are the possible options:
        |- esurl : ElasticSearch UR
        |- starturl : first portion of the URL on which we iterate to get the file's S3 keys
        |- midurl : middle portion (changes while iterating)
        |- endurl : end portion (additionnal options for the GET request)
        |- do : what the program will do (adminremote to index remote files, adminlocal to index local files)
        |- localinput : the local folder used as source for adminlocal
        |- bucket : the S3 source bucket
        |As an example of the syntax, to change the localinput to MYFOLDER, one would write "localinput:MYFOLDER"
      """.stripMargin
    println(help)
    System.exit(0)
  }

  def main(args: Array[String]) {

    def mapFromArgs: Map[String, String] = {

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
        "starturl" -> "https://kf-api-dataservice.kidsfirstdrc.org",
        "midurl" -> "/genomic-files",
        "endurl" -> "file_format=pdf&limit=100",
        "do" -> "adminremote",
        "localinput" -> "./input/",
        "bucket" -> "TEMP"
        //"languages" -> "eng"
      )

      mapFromArgsIter(args, defaults)
    }

    argMap = mapFromArgs

    ocrParser = new OCRParser
    nlpParser = new NLPParser
    esIndexer = new ESIndexer(argMap("esurl"))
    s3Downloader = new S3Downloader(argMap("bucket"))

    val startTime = System.currentTimeMillis()

    val f1 = Future {
      indexParticipants("https://kf-api-dataservice.kids-first.io", "/participants", "limit=100&visible=true")
    }.flatten
    val f2 = Future {
      if (argMap("do").equals("adminremote")) {
        indexPDFRemote(argMap("starturl"), argMap("midurl"), argMap("endurl"))
      } else {
        indexPDFLocal(argMap("localinput"))
      }
    }.flatten

    val f = Future.sequence(Seq(f1, f2))
    Await.result(f, Duration.Inf)

    esIndexer.cleanup()

    println("took " + (System.currentTimeMillis() - startTime) / 1000 + " seconds")
    System.exit(0)
  }

}