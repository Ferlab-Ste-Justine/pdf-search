import java.io.{File, FileInputStream, InputStream}

import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.annotation.tailrec
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable
import scala.concurrent.duration.Duration
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

        /*
        val titleText = getFiles("./input/").map({ x =>

            val text = ocrParser.parsePDF(x)

            (nlpParser.getNouns(text), text)
        })

        println(titleText)

        val temp = nlpParser.getNouns("Brain cancer is bad. This sucks: it is not fun, man...")

        println(temp)*/

        adminIndexFilesLocal("./input/")
    }

    /**
      * Builds a scala list from a java/scala Array while applying function f in one go. Avoids something like:
      *     array.map( x => f(x) ).toList
      * Insread, we do this like: array.mapToList( x => f(x) )
      *
      * This is thus in one pass while the other way is in two
      *
      * @param array the array to transform
      * @param f the function to apply on its elements
      * @tparam T generic, type of the original Array's elements
      * @tparam B generic, type of the created list's elements
      * @return the list
      */
    def arrayToListWithF[T, B](array: Array[T], f: T => B): List[B] = {
        /**
          * Builds the list element by element
          *
          * @param array the array to build
          * @param f the function to use on its elements
          * @param acc the accumulator building the list
          * @return the list
          */
        @tailrec
        def listBuild(array: Array[T], f: T => B, acc: List[B]): List[B] = array match {
            case Array() => acc //if the array is empty, we're done!
            case Array(x, _*) =>
                listBuild(array.drop(1), f, acc :+ f(x)) //drop the array's head and continue
        }

        /*
        If pattern matching is slow (?), this also works:

        @tailrec
        def listBuild(index: Int, array: Array[T], f: T => B, acc: List[B]): List[B] = {
            if(index >= array.length) acc   //the accumulator is now done
            else listBuild(index + 1, array, f, acc :+ f(array(index))) //increment the index and add an element to acc
        }
         */

        listBuild(array, f, List())
    }

    /**
      * Indexes files from a local directory into ES
      *
      * @param path the path to the folder containing the files
      */
    def adminIndexFilesLocal(path: String): Unit = {
        /*
        To use Future.sequence we need a List, not an Array.

        So we're transforming the array in-place using arrayToListWithF
         */

        val futures = arrayToListWithF(getFiles(path),  //futures will be a List of all the Futures we launched
            (file: File) => {                   //for every file
                val name = getFileName(file)

                val stream = new FileInputStream(file)

                val future = Future[Unit] {     //start a future to do: OCR -> NLP -> ES
                    val text = ocrParser.parsePDF(stream)
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

                future  //return the future to put it in the list
            }
        )

        val allFutures = Future.sequence(futures)   //transform List[Future[Unit]] into Future[List[Unit]]
        Await.result(allFutures, Duration.Inf)      //then, wait until ALL the futures are done

        println("" + futures.length + " files indexed\nAdmin indexing done. Exiting now...")
        System.exit(0)
    }

    def getFileName(file: File): String = file.getName.replaceAll("(.pdf)$", "")

    /**
      * TODO TEMP change this to get all files from ES once
      * @param path
      * @return
      */
    def getFiles(path: String): Array[File] = new File(path).listFiles()


}
