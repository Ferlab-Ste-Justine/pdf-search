import java.io.{BufferedWriter, File, FileWriter}

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
        //TODO the instantiations will use command line args once we have more details on the project
        ocrParser = new OCRParser
        nlpParser = new NLPParser
        esIndexer = new ESIndexer

        //adminIndexFilesLocal("./input/")
        adminIndexFilesLocal("./input/")
    }

    //TODO def adminIndexFiles qui est comme adminIndexFilesLocal mais qui utilise le S3Downloader pour prendre les
    //TODO fichiers a partir de S3

    //TODO def adminFuture, qui return un future et prend en param le nom d'un fichier et un stream ou file idk
    //TODO comme ca, adminIndexFilesLocal et adminIndexFiles appelleraient juste adminFuture(nom, stream/file) betement
    //TODO puisque le code est le meme dans le fond...

    //TODO 3

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
                val keywords = nlpParser.keywordise(wordTags)

                val list = List(
                    AdminFile(name, text),
                    AdminWord(name, wordTags),
                    AdminFileWordKeyword(name, text, wordTags, keywords),
                    AdminKeyword(name, keywords))

                esIndexer.bulkIndex(list)
            }
        })

        println("Files indexed.\nAdmin indexing done. Exiting now...")
        System.exit(0)
    }

    //TODO peut etre prendre ce qui est dans 80% des etudes voulues au lieu de 25% des etudes random? idk
    def adminGetKeywords(path: String): Unit = {
        val lemmaSets: Seq[Set[String]] = Pool.distribute(getFiles(path), (file: File) => { //for every file
            Future[Set[String]] {     //start a future to do: OCR -> NLP -> ES
                val text = ocrParser.parsePDF(file)
                nlpParser.getLemmas(text).toSet
            }
        })

        val nbOfStudies: Float = lemmaSets.length

        val writer = new BufferedWriter(new FileWriter("./words/blacklist.tsv", false))

        val lemmaMap = lemmaSets.flatten.foldLeft(Map[String, Int]()){ (acc, noun: String) =>
            if(!acc.isDefinedAt(noun)) acc + (noun -> 1)
            else acc + (noun -> (acc(noun)+1))

        }.toList

        val lemmaList: Seq[String] = Pool.distributeIt(lemmaMap, (subMap: Iterable[(String, Int)]) => {
            Future[String] { //start a future to do: OCR -> NLP -> ES
                subMap.foldLeft("") { (acc, tuple: (String, Int)) =>
                    if (tuple._2 / nbOfStudies >= 0.25) acc + tuple._1 + "\n"
                    else acc + ""
                }
            }

        }, lemmaMap.length / Runtime.getRuntime.availableProcessors())

        lemmaList.foreach( writer.append )

        writer.close()

        println("File keylemmas extracted.\nAdmin keyword extraction done. Exiting now...")
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
