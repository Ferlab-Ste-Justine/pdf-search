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

        indexParticipants("https://kf-api-dataservice-qa.kids-first.io", "/participants", "limit=100&visible=true")


        val temp = URLIterator.batchedApplyOnAllFrom("https://kf-api-dataservice-qa.kids-first.io",
            "/participants", "limit=100&visible=true", List("kf_id", "ethnicity", "race", "gender"), List("family"))(identity)

        if(argMap("do").equals("adminremote")) {
            adminIndexFilesRemote(argMap("starturl"), argMap("midurl"), argMap("endurl"))
        } else if(argMap("do").equals("adminlocal")) {
            adminIndexFilesLocal(argMap("localinput"))

        } else printHelp
    }

    def indexParticipants(start: String, mid: String, end: String): Unit = {

        /* TODO

        On est pas capables de finir ceci. Comme dans notre callback on doit accéder au dataservice aussi (pour les family), on est complètement bloqués sur
        l'IO. On échoue avec Caused by: java.lang.OutOfMemoryError: unable to create native thread: possibly out of memory or process/resource limits reached

        https://stackoverflow.com/questions/16789288/java-lang-outofmemoryerror-unable-to-create-new-native-thread
        http://www.beyondthelines.net/computing/scala-future-and-execution-context/

        Il faut soit abandonner les Futures ici, soit tout downloader sur disque, pour ensuite pouvoir utiliser des futures lorsqu'on demande les familly.

        L'ajout de Blocking n'a rien changé.

        Ça semble pire lorsqu'on ne batch pas puisqu'on crée un future qui appelle le URLiterator par participant concuremment au lieu de séquentiellement pour la batch,
        mais au moins on arrive à en faire quelques uns avant de crasher. Ça fonctionne avec blocking, cependant.

         */

        /* AVEC BATCHING
        val participantsFuture = URLIterator.batchedApplyOnAllFrom(start,
            mid, end, List("kf_id", "ethnicity", "race", "gender"), List("family")){ batch: List[Map[String, String]] =>
            Future[Unit] {
                blocking{
                    val participantList = batch.foldLeft(List[IndexingRequest]()) { (acc, participant: Map[String, String]) =>
                        val family = URLIterator.applyOnAllFrom(start, participant("_links.family"), end="", List("kf_id") )(identity)
                        val text = "KF_ID: "+participant("kf_id")+". Ethnicity: "+participant("ethnicity")+". Race: "+participant("race")+". Gender: "+participant("gender")+". Family_id: "+family+"."
                        val words = participant.values.slice(1, 3)

                        acc :+ IndexingRequest("Participant "+participant("kf_id"), text, words, "participant", "participant", participant("kf_id"))
                    }

                    esIndexer.bulkIndex(participantList)
                }

            }
        }*/

        // SANS BATCHING: ne semble jamais finir???
        val participantsFuture: List[Future[Unit]] = URLIterator.applyOnAllFrom(start,
            mid, end, List("kf_id", "ethnicity", "race", "gender"), List("family")){ participant: Map[String, String] =>
                Future[Unit] {
                    blocking{
                        val family = URLIterator.applyOnAllFrom(start, participant("_links.family"), end="", List("kf_id") )(identity)
                        val text = "KF_ID: "+participant("kf_id")+". Ethnicity: "+participant("ethnicity")+". Race: "+participant("race")+". Gender: "+participant("gender")+". Family_id: "+family+"."
                        val words = participant.values.slice(1, 3)

                        esIndexer.index(IndexingRequest("Participant "+participant("kf_id"), text, words, "participant", "participant", participant("kf_id")))
                    }

                }
            }

        Await.result(Future.sequence(participantsFuture), Duration.Inf) //quand on arrive ici, on s'arrête même si on a pas finit?

        println("CECI N'EST JAMAIS IMPRIMÉ! BUG!")
        println("Participants indexing done. exiting now...")
        System.exit(1)
    }

    def adminIndex(pdf: InputStream, name: String, dataType: String = "local", fileFormat: String = "pdf", kfId: String = "local"): String = {    //do: OCR -> NLP -> ES

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
        val futures = URLIterator.applyOnAllFrom(start, mid, end, List("external_id", "data_type", "file_format", "file_name", "kf_id")) { edffk =>
            Future[String] {     //start a future to do: S3 -> OCR -> NLP -> ES
                adminIndex(s3Downloader.download(edffk("external_id")), edffk("file_name"), edffk("data_type"), edffk("file_format"), edffk("kf-id"))
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
        //esIndexer.initAdminIndexes
        val start = System.currentTimeMillis()

        /*
        Need an englobing Future to start the smaller Futures sequentially (prevents the indexation of 2-3 documents
        from blocking the threads).

        Without this future: 50s for 20 docs
        With this future: 40s for 20 docs!
         */
        val futures = Future.traverse(getFiles(path).toList) { file: File =>
            Future[String] {     //start a future to do: OCR -> NLP -> ES
                adminIndex(new FileInputStream(file), getFileName(file))
            }
        }

        printReport(Await.result(futures, Duration.Inf))

        println("Files indexed.\nAdmin indexing done. Exiting now...")
        println("Took: "+(System.currentTimeMillis()-start)/1000+"s for "+getFiles(path).length+" documents")
        System.exit(0)
    }

    def getFileName(file: File): String = file.getName.replaceAll("(.pdf)$", "")

    def getFiles(path: String): Array[File] = new File(path).listFiles()
}
