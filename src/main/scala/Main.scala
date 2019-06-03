import java.io.{File, FileInputStream, InputStream}

import Tasker._

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

    def printHelp: Unit = {
        println("HELP: command line arguments are of form key:value and are not case sensitive")
        println("Here are the possible options:")
        println("\tesurl : ElasticSearch URL" +
            "\n\tstarturl : first portion of the URL on which we iterate to get the file's S3 keys" +
            "\n\tmidurl : middle portion (changes while iterating)" +
            "\n\tendurl : end portion (additionnal options for the GET request)" +
            "\n\tdo : what the program will do (adminremote to index remote files, adminlocal to index local files)" +
            "\n\tlocalinput : the local folder used as source for adminlocal" +
            "\n\tbucket : the S3 source bucket")
        print("As an example of the syntax, to change the localinput to MYFOLDER, one would write \"localinput:MYFOLDER\"")
        System.exit(0)
    }

    def main(args: Array[String]) {

        def mapFromArgs: Map[String, String] = {

            if(args.contains("--help") || args.contains("--h")) printHelp

            @tailrec
            def mapFromArgsIter(argList: Array[String], argMap: Map[String, String]): Map[String, String] = argList match {
                case Array() => argMap
                case _ =>
                    val tail = argList.tail
                    val keyval = argList.head.toLowerCase.split(":")

                    mapFromArgsIter(tail, argMap + (keyval(0) -> keyval(1)))
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

        Tasks(
            Future[List[String]] { List("allo", "salut")} ,
            indexParticipants("https://kf-api-dataservice.kids-first.io", "/participants", "limit=100&visible=true"),
            if(argMap("do").equals("adminremote")) {
                indexPDFRemote(argMap("starturl"), argMap("midurl"), argMap("endurl"))
            } else {
                indexPDFLocal(argMap("localinput"))
            }
        )

        esIndexer.cleanup()

        println("took "+(System.currentTimeMillis()-startTime)/1000+" seconds")
        System.exit(0)
    }

    def indexParticipants(start: String, mid: String, end: String) = {
        URLIterator.batchedApplyOnAllFrom(start, mid, end, List("kf_id", "ethnicity", "race", "gender"), List("family"), batchSize = 1500) { participants: List[Map[String, String]] =>
            Future[Unit] {
                val futures = Future.traverse(participants) { participant: Map[String, String] =>
                    Future[IndexingRequest] {
                        val familyLink = participant("_links.family")
                        val family = familyLink.substring(familyLink.lastIndexOf('/') + 1, familyLink.length)
                        val text = "KF_ID: " + participant("kf_id") + ". Ethnicity: " + participant("ethnicity") + ". Race: " + participant("race") + ". Gender: " + participant("gender") + ". Family_id: " + family + "."
                        val words = participant.values.slice(1, 3)

                        IndexingRequest("Participant " + participant("kf_id"), text, words, "participant", "participant", participant("kf_id"))
                    }
                }

                esIndexer.bulkIndex(Await.result(futures, Duration.Inf))
            }
        }
    }

    /**
      * Indexes PDFs into ES.
      *
      * @param pdf the PDF as an InputStream. Uses call-by-name to create a future on the download of said stream
      * @param name the name of the PDF
      * @param dataType the datatype of the PDF
      * @param fileFormat the format of the PDF (always PDF, but we're not hard-coding it...)
      * @param kfId the Kid's First ID of the PDF
      * @return String representing wether or not the indexing succeeded
      */
    def indexPDF(pdf: => InputStream, name: String, dataType: String = "local", fileFormat: String = "pdf", kfId: String = "local"): Future[String] = Future[String] {    //do: OCR -> NLP -> ES
        try {
            val text = ocrParser.parsePDF(pdf)

            esIndexer.index(IndexingRequest(name, text, nlpParser.getLemmas(text), dataType, fileFormat, kfId))

            s"$name"

        } catch {
            case e: java.net.ConnectException =>
                e.printStackTrace()
                println("ES or connection error; exiting now...")
                System.exit(1)
                ""
            case _: Exception => s"Failed indexing $name"
        }
    }

    /**
      * Prints things in the list of pdf indexing result. Returns
      * @param list
      * @return
      */
    def printReport(list: List[String]) = {
        def printtab(str: String): Unit = println("\t"+str)

        @tailrec
        def printIter(main: List[String], failures: List[String]): Unit = main match {
            case x :: tail =>
                if(x.startsWith("Failed")) printIter(tail, failures :+ x)
                else {
                    printtab(x)
                    printIter(tail, failures)
                }
            case List() =>
                println("Failures:")
                failures.foreach(printtab)
        }

        println("Successes:")
        printIter(list, List())

        list
    }

    def indexPDFRemote(start: String, mid: String, end: String) = {
        URLIterator.applyOnAllFrom(start, mid, end, List("external_id", "data_type", "file_format", "file_name", "kf_id")) { edffk =>
            //start a future to do: S3 -> OCR -> NLP -> ES
            indexPDF(s3Downloader.download(edffk("external_id")), edffk("file_name"), edffk("data_type"), edffk("file_format"), edffk("kf-id"))
        }
    }

    /**
      * Indexes files from a local directory into ES
      *
      * @param path the path to the folder containing the files
      */
    def indexPDFLocal(path: String) = {
        getFiles(path).toList.map { file: File =>
            //start a future to do: OCR -> NLP -> ES
            indexPDF(new FileInputStream(file), getFileName(file))
        }
    }

    def getFileName(file: File): String = file.getName.replaceAll("(.pdf)$", "")

    def getFiles(path: String): Array[File] = new File(path).listFiles()

}