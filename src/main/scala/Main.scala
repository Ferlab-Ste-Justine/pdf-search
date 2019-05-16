import java.io.{BufferedWriter, File, FileWriter, InputStream}

import org.elasticsearch.monitor.jvm.JvmStats.GarbageCollector

import scala.collection.mutable
import scala.collection.mutable.{ArrayOps, ListBuffer}
import scala.concurrent
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{CanAwait, Future, _}
import scala.concurrent.duration._
import scala.util.{Failure, Success}


//https://github.com/overview/pdfocr

//https://stackoverflow.com/questions/4827924/is-tesseractan-ocr-engine-reentrant thread safe: FUTURES!

object Main {
    var ocrParser: OCRParser = _
    var nlpParser: NLPParser = _
    var esIndexer: ESIndexer = _
    var s3Downloader: S3Downloader = _

    def main(args: Array[String]) {
        //TODO the instantiations will use command line args once we have more details on the project
        ocrParser = new OCRParser
        nlpParser = new NLPParser
        esIndexer = new ESIndexer
        s3Downloader = new S3Downloader("TEMP")

        URLIterator.applyOnAllFrom("https://kf-api-dataservice.kidsfirstdrc.org", "/genomic-files", "file_format=pdf&limit=100")(println)

        //adminIndexFilesLocal("./input/")
        //adminIndexFilesLocalWithKeywordsTemp("./input/")
    }

    //TODO def adminIndexFiles qui est comme adminIndexFilesLocal mais qui utilise le S3Downloader pour prendre les
    //TODO fichiers a partir de S3

    //TODO def adminFuture, qui return un future et prend en param le nom d'un fichier et un stream ou file idk
    //TODO comme ca, adminIndexFilesLocal et adminIndexFiles appelleraient juste adminFuture(nom, stream/file) betement
    //TODO puisque le code est le meme dans le fond...

    //TODO 3

    def adminIndexFilesRemote(start: String, mid: String, end: String): Unit = {
        val futures: List[Future[Unit]] = URLIterator.applyOnAllFrom(start, mid, end)( (url: String) => {
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
        })

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
                val keywords = nlpParser.keywordise(wordTags)

                val list = List(
                    AdminFile(name, text),
                    AdminWord(name, wordTags),
                    //AdminFileWordKeyword(name, text, wordTags, keywords),
                    AdminKeyword(name, keywords))

                esIndexer.bulkIndex(list)
            }
        })

        println("Files indexed.\nAdmin indexing done. Exiting now...")
        System.exit(0)
    }

    def adminIndexFilesLocalWithKeywordsTemp(path: String): Unit = {

        Future.traverse(getFiles(path).toList){ file: File => {                   //for every file
            Future[(String, Array[(String, String)], Map[String, Int], String)] {     //start a future to do: OCR -> NLP -> ES
                val text = ocrParser.parsePDF(file)
                val wordTags = nlpParser.getTokenTags(text)
                val lemmaMap = nlpParser.getLemmas(text).foldLeft(Map[String, Int]()){ (acc, lemma: String) =>
                    if(!acc.isDefinedAt(lemma)) acc + (lemma -> 1)
                    else acc + (lemma -> (acc(lemma)+1))
                }

                (text, wordTags, lemmaMap, getFileName(file))
            }

        }}.flatMap{ twltList: Seq[(String, Array[(String, String)], Map[String, Int], String)] =>

            val idfMap = nlpParser.keywordLearn( twltList.map( tuple => tuple._3.keys.toList ) )

            Future.traverse(twltList) { twlt =>
                Future[Unit] {
                    val name = twlt._1
                    val text = twlt._4
                    val wordTags = twlt._2
                    val lemmas = nlpParser.keywordTake(twlt._3, idfMap)

                    val list = List(
                        AdminFile(name, text),
                        AdminWord(name, wordTags),
                        //AdminFileWordKeyword(name, text, wordTags, lemmas),
                        AdminKeyword(name, lemmas)
                    )

                    esIndexer.bulkIndex(list)
                }
            }

        }.onComplete{
            case Success(_) =>
                println("Files indexed.\nAdmin indexing done. Exiting now...")
                System.exit(0)
            case Failure(e: Exception) =>
                e.printStackTrace()
                println("Something went wrong. Exiting now...")
                System.exit(1)
        }
    }

    def adminIndexFilesLocalWithKeywords(path: String): Unit = {

        val textTokenTagsLemmas = Pool.distribute(getFiles(path), (file: File) => {                   //for every file
            Future[(String, Array[(String, String)], Map[String, Int], String)] {     //start a future to do: OCR -> NLP -> ES
                val text = ocrParser.parsePDF(file)
                val wordTags = nlpParser.getTokenTags(text)
                val lemmaMap = nlpParser.getLemmas(text).foldLeft(Map[String, Int]()){ (acc, lemma: String) =>
                    if(!acc.isDefinedAt(lemma)) acc + (lemma -> 1)
                    else acc + (lemma -> (acc(lemma)+1))
                }

                (text, wordTags, lemmaMap, getFileName(file))
            }
        })

        val tfMapList: Seq[Map[String, Int]] = textTokenTagsLemmas.map(temp => temp._3 )

        val idfMap = nlpParser.keywordLearn( tfMapList.map( tfMap => tfMap.keys.toList ) )

        val ttWTM = textTokenTagsLemmas.indices.map{ i =>
            (textTokenTagsLemmas(i)._4, textTokenTagsLemmas(i)._1, textTokenTagsLemmas(i)._2, tfMapList(i))
        }

        Pool.distribute(ttWTM, (tuple: (String, String, Array[(String, String)], Map[String, Int])) => {
            Future[Unit] {     //start a future to do: OCR -> NLP -> ES
                val name = tuple._1
                val text = tuple._2
                val wordTags = tuple._3
                val lemmas = nlpParser.keywordTake(tuple._4, idfMap)

                val list = List(
                    AdminFile(name, text),
                    AdminWord(name, wordTags),
                    //AdminFileWordKeyword(name, text, wordTags, lemmas),
                    AdminKeyword(name, lemmas)
                )

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
