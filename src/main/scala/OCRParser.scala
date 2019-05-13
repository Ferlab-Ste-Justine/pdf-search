import java.awt.image.BufferedImage
import java.io.{File, FileInputStream, InputStream}
import java.nio.file.{Path, Paths}
import java.util.Locale

import net.sourceforge.tess4j.Tesseract
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.{ImageType, PDFRenderer}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.annotation.tailrec

class OCRParser(languages: String = "eng") {

    /*
        Bug on tesseract 4.0.0: crash when calling procedure is not tesseract itself. To fix, we're changing the locale
        to C using java's Native library (see CLibrary.java)

        https://github.com/nguyenq/tess4j/issues/106
    */
    CLibrary.INSTANCE.setlocale(CLibrary.LC_ALL, "C")
    CLibrary.INSTANCE.setlocale(CLibrary.LC_NUMERIC, "C")
    CLibrary.INSTANCE.setlocale(CLibrary.LC_CTYPE, "C")

    private def getTesseract: Tesseract = {
        val tesseract: Tesseract = new Tesseract()  //the Tesseract used to do the OCR.
        tesseract.setDatapath("./tessdata")         //we're using the local tessdata folder (fails when using global...)
        tesseract.setLanguage(languages)            //by default we're reading English (TODO add more tessdata)

        tesseract
    }

    /**
      * Syntactic sugar for parsePDF(pdf: Stream)
      *
      * @param pdf the java.io.File to input to parsePDF
      * @return the text of the pdf
      */
    def parsePDF(pdf: File): String = parsePDF(new FileInputStream(pdf))

    /**
      * Parses the requested pdf stream into a string
      *
      * @param pdf The pdf (as a stream)
      * @return The string (text) of the pdf
      */
    def parsePDF(pdf: InputStream): String = {
        /*
        https://sourceforge.net/p/tess4j/discussion/1202293/thread/4562eccb/

        Each thread needs its tesseract
         */
        val tesseract = getTesseract

        val document = PDDocument.load(pdf)

        val renderer = new PDFRenderer(document)

        /*
        Ideally, the renderer would offer a entireDocumentAsImage method or a getAllPagesAsImages; or it could take a
        single page and render it.

        Unfornuately, it doesn't, so we either have to map on the List of the indexes of the document's pages, or we
        have to use parseRec to recursively OCR every page. Both methods use the indexes of the pages, but the map
        is more readable.

        The real best way would simply be an Imperative-style for loop, though, avoiding having to create intermediary
        data structures...
         */

        /* Fonctionnal style is O(2n)
        val asText = List.range(0, document.getNumberOfPages).foldLeft("") {
            (acc, page) => acc + parsePage(document, renderer, page)
        }*/

        //imperative style is O(n)
        var asText = ""
        for(i <- 0 until document.getNumberOfPages) asText += parsePage(tesseract, document, renderer, i)

        document.close()    //need to close the document before exiting this function

        asText
    }

    /*
    @tailrec
    private def parseRec(accumulator: String, index: Int, stop: Int, renderer: PDFRenderer): String = {
        if(index >= stop) accumulator
        else parseRec(accumulator + tesseract.doOCR(renderer.renderImageWithDPI(index, 750, ImageType.GRAY)), index+1, stop, renderer)
    }*/

    /**
      * Takes the given page as as grayscale bufferedImage and returns its OCR
      *
      * https://github.com/nguyenq/tess4j/issues/106
      *
      * @param document The document we're reading from
      * @param renderer The document's PDFRenderer
      * @param index The requested page's index
      * @return The OCR of the page
      */
    private def parsePage(tesseract: Tesseract, document: PDDocument, renderer: PDFRenderer, index: Int): String = {
        println("\tReading page "+index)
        tesseract.doOCR(renderer.renderImageWithDPI(index, 750, ImageType.GRAY))
    }

    /* Possibly useful if we want to make OCR futures in the #future

    def makeDistributable(document: PDDocument, renderer: PDFRenderer): ListBuffer[(PDDocument, PDFRenderer, Int)] = {
        val buffer = new ListBuffer[(PDDocument, PDFRenderer, Int)]

        for(i <- 0 until document.getNumberOfPages) buffer.append((document, renderer, i))

        buffer
    }

    private def threadSafeParsePage(tuple: (PDDocument, PDFRenderer, Int)): String =
        parsePage(getTesseract, tuple._1, tuple._2, tuple._3)*/
}