import java.io.{File, FileInputStream, InputStream}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, _}


//https://github.com/overview/pdfocr

//https://stackoverflow.com/questions/4827924/is-tesseractan-ocr-engine-reentrant thread safe: FUTURES!

//TODO conf https://github.com/lightbend/config

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


        if(argMap("do").equals("adminremote")) {
            adminIndexFilesRemote(argMap("starturl"), argMap("midurl"), argMap("endurl"))
        } else if(argMap("do").equals("adminlocal")) {
            adminIndexFilesLocal(argMap("localinput"))

        } else printHelp
    }

    def adminIndex(pdf: InputStream, name: String): String = {    //do: OCR -> NLP -> ES

        try {
            val text = ocrParser.parsePDF(pdf)
            val wordTags = nlpParser.getTokenTags(text)
            val lemmas = nlpParser.getLemmas(text)

            val list = List(
                AdminFile(name, text),
                AdminWord(name, wordTags),
                AdminFileWord(name, text, wordTags)
            )

            esIndexer.bulkIndex(list)

            s"Indexed $name."
        } catch {
            case _: Exception => s"Failed indexing $name: file is corrupt, ES URL is wrong, etc"
        }
    }

    def printReport(list: List[String]): Unit = {
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
    }

    def adminIndexFilesRemote(start: String, mid: String, end: String): Unit = {
        esIndexer.initAdminIndexes

        val futures: List[Future[String]] = URLIterator.applyOnAllFrom(start, mid, end){ url: String =>
            Future[String] {     //start a future to do: S3 -> OCR -> NLP -> ES
                adminIndex(s3Downloader.download(url), url)
            }
        }

        printReport(Await.result(Future.sequence(futures), Duration.Inf))

        println("Files indexed.\nAdmin indexing done. Exiting now...")
        System.exit(0)
    }

    /**
      * Indexes files from a local directory into ES
      *
      * @param path the path to the folder containing the files
      */
    def adminIndexFilesLocal(path: String): Unit = {
        esIndexer.initAdminIndexes

        val futures: Future[List[String]] = Future.traverse(getFiles(path).toList) { file: File =>
            Future[String] {     //start a future to do: OCR -> NLP -> ES
                println(getFileName(file))
                adminIndex(new FileInputStream(file), getFileName(file))
            }
        }

        printReport(Await.result(futures, Duration.Inf))

        println("Files indexed.\nAdmin indexing done. Exiting now...")
        System.exit(0)
    }

    def getFileName(file: File): String = file.getName.replaceAll("(.pdf)$", "")

    def getFiles(path: String): Array[File] = new File(path).listFiles()
}
