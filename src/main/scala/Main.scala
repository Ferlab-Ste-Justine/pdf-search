import java.io.{BufferedWriter, File, FileWriter, InputStream}
import java.net.URL

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, _}
import scala.util.{Failure, Success}


//https://github.com/overview/pdfocr

//https://stackoverflow.com/questions/4827924/is-tesseractan-ocr-engine-reentrant thread safe: FUTURES!

//TODO conf https://github.com/lightbend/config

object Main {

    var ocrParser: OCRParser = _
    var nlpParser: NLPParser = _
    var esIndexer: ESIndexer = _
    var s3Downloader: S3Downloader = _
    var argMap: Map[String, String] = _

    def main(args: Array[String]) {

        def mapFromArgs: Map[String, String] = {

            if(args.contains("--help") || args.contains("--h")) {
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

        //TODO the instantiations will use command line args once we have more details on the project
        ocrParser = new OCRParser
        nlpParser = new NLPParser
        esIndexer = new ESIndexer(argMap("esurl"))
        s3Downloader = new S3Downloader(argMap("bucket"))


        if(argMap("do").equals("adminremote")) {
            adminIndexFilesRemote(argMap("starturl"), argMap("midurl"), argMap("endurl"))
        } else if(argMap("do").equals("adminlocal")) {
            adminIndexFilesLocal(argMap("localinput"))
        }
        //URLIterator.applyOnAllFrom("https://kf-api-dataservice.kidsfirstdrc.org", "/genomic-files", "file_format=pdf&limit=100")(println)
    }

    def adminIndexFilesRemote(start: String, mid: String, end: String): Unit = {
        val futures: List[Future[Unit]] = URLIterator.applyOnAllFrom(start, mid, end){ url: String =>
            Future[Unit] {     //start a future to do: S3 -> OCR -> NLP -> ES
                val name = url
                val pdfStream: InputStream = s3Downloader.download(url)
                val text = ocrParser.parsePDF(pdfStream)
                val wordTags = nlpParser.getTokenTags(text)

                val list = List(
                    AdminFile(name, text),
                    AdminWord(name, wordTags),
                    AdminFileWord(name, text, wordTags),
                )

                esIndexer.bulkIndex(list)
            }
        }

        Await.result(Future.sequence(futures), Duration.Inf)

        println("Files indexed.\nAdmin indexing done. Exiting now...")
        System.exit(0)
    }

    /**
      * Indexes files from a local directory into ES
      *
      * @param path the path to the folder containing the files
      */
    def adminIndexFilesLocal(path: String): Unit = {
        Pool.distribute(getFiles(path), (file: File) => {                   //for every file
            val name = getFileName(file)

            Future[Unit] {     //start a future to do: OCR -> NLP -> ES
                val text = ocrParser.parsePDF(file)
                val wordTags = nlpParser.getTokenTags(text)
                //val keywords = nlpParser.keywordise(wordTags)

                val list = List(
                    AdminFile(name, text),
                    AdminWord(name, wordTags),
                    AdminFileWord(name, text, wordTags),
                    //AdminKeyword(name, keywords))
                )

                esIndexer.bulkIndex(list)
            }
        })

        println("Files indexed.\nAdmin indexing done. Exiting now...")
        System.exit(0)
    }

    def getFileName(file: File): String = file.getName.replaceAll("(.pdf)$", "")

    /**
      * TODO TEMP change this to get all files from ES
      * @param path
      * @return
      */
    def getFiles(path: String): Array[File] = new File(path).listFiles()
}
