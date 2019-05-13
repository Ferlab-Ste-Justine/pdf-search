import java.io.File

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, _}
import scala.util.{Failure, Success}


//https://github.com/overview/pdfocr

//https://stackoverflow.com/questions/4827924/is-tesseractan-ocr-engine-reentrant thread safe: FUTURES!

object Main {
    var ocrParser: OCRParser = _
    var nlpParser: NLPParser = _
    var esIndexer: ESIndexer = _

    def main(args: Array[String]) {
        ocrParser = new OCRParser
        nlpParser = new NLPParser
        esIndexer = new ESIndexer

        adminIndexFilesLocal("./input/")
    }

    //TODO def adminIndexFiles qui est comme adminIndexFilesLocal mais qui utilise le S3Downloader pour prendre les
    //TODO fichiers a partir de S3

    //TODO def adminFuture, qui return un future et prend en param le nom d'un fichier et un stream ou file idk
    //TODO comme ca, adminIndexFilesLocal et adminIndexFiles appelleraient juste adminFuture(nom, stream/file) betement
    //TODO puisque le code est le meme dans le fond...

    /**
      * Indexes files from a local directory into ES
      *
      * @param path the path to the folder containing the files
      */
    def adminIndexFilesLocal(path: String): Unit = {
        Pool.distribute(getFiles(path), (file: File) => {                   //for every file
            val name = getFileName(file)

            val future = Future[Unit] {     //start a future to do: OCR -> NLP -> ES
                val text = ocrParser.parsePDF(file)
                val wordTags = nlpParser.getTokenTags(text)

                esIndexer.bulkIndex(List(AdminFile(name, text), AdminWord(name, wordTags), AdminFileWord(name, text, wordTags)))
            }

            future.onComplete {             //when said future completes
                case Success(u: Unit) =>        //print success message
                    println("" + name + " has been indexed")
                case Failure(e: Exception) =>   //otherwise exit if a problem occured
                    println("A problem occured. Please make sure your ES url is valid and try again.")
                    e.printStackTrace()
                    System.exit(1)
            }

            future
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
