import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.{Path, Paths}
import java.util.Locale

import net.sourceforge.tess4j.Tesseract
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.{ImageType, PDFRenderer}

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

    val tesseract: Tesseract = new Tesseract()  //the Tesseract used to do the OCR. Instantiated only once
    tesseract.setDatapath("./tessdata")         //we're using the local tessdata folder
    tesseract.setLanguage(languages)            //by default we're reading English (TODO add more tessdata)

    /**
      * Parses the requested pdf into a string
      *
      * @param pdf The pdf (must be a java.io.File)
      * @return The string (text) of the pdf
      */
    def parsePDF(pdf: File): String = {
        println("Reading document " + pdf.getName)

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

        /*
        val asText = List.range(0, document.getNumberOfPages).foldLeft("") {
            (acc, page) => acc + parsePage(document, renderer, page)
        }*/

        var asText = ""
        for(i <- 0 until document.getNumberOfPages) asText += parsePage(document, renderer, i)

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
    def parsePage(document: PDDocument, renderer: PDFRenderer, index: Int): String = {
        println("\tReading page "+index)
        tesseract.doOCR(renderer.renderImageWithDPI(index, 750, ImageType.GRAY))
    }
}