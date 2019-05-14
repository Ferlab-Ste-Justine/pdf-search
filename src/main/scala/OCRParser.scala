import java.io.{File, FileInputStream, InputStream}

import net.sourceforge.tess4j.Tesseract
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.{ImageType, PDFRenderer}
import org.apache.pdfbox.text.PDFTextStripper

class OCRParser(languages: String = "eng") {

    /*
        Bug on tesseract 4.0.0: crash when calling procedure is not tesseract itself. To fix, we're changing the locale
        to C using java's Native library (see interface CLibrary.java)

        https://github.com/nguyenq/tess4j/issues/106
    */
    CLibrary.INSTANCE.setlocale(CLibrary.LC_ALL, "C")
    CLibrary.INSTANCE.setlocale(CLibrary.LC_NUMERIC, "C")
    CLibrary.INSTANCE.setlocale(CLibrary.LC_CTYPE, "C")

    /**
      * Syntactic sugar for parsePDF(pdf: Stream)
      *
      * @param pdf the java.io.File to input to parsePDF
      * @return the text of the pdf
      */
    def parsePDF(pdf: File): String = parsePDF(new FileInputStream(pdf))

    /**
      * Parses the requested pdf stream into a string.
      *
      * Uses direct extraction if possible and OCR otherwise
      *
      * @param pdf The pdf (as a stream)
      * @return The string (text) of the pdf
      */
    def parsePDF(pdf: InputStream): String = {
        val document = PDDocument.load(pdf)

        /*
        Try-catch-finally in scala returns whichever value of try catch works

        https://stackoverflow.com/questions/18685573/try-catch-finally-return-value/18685727
         */

        try {
            directExtract(document)
        } catch {
            case _: Exception => ocrExtract(document)
        } finally {
            document.close()    //need to close the document before exiting this function
        }
    }

    /**
      * Tries to extract text from a PDDocument using the "overline"/"ctrl+c" method. If we get less than 300 chars, we
      * consider the call to have failed and throw an Exception
      *
      * @param document the document to extract from
      * @throws Exception to indicate failure
      * @return the text of the document should we succeed
      */
    @throws[Exception]
    def directExtract(document: PDDocument): String = {
        val text = new PDFTextStripper().getText(document)

        if(text.length < 300) throw new Exception("OCR needed")

        text
    }

    private def getTesseract: Tesseract = {
        val tesseract: Tesseract = new Tesseract()  //the Tesseract used to do the OCR.
        tesseract.setDatapath("./tessdata")         //we're using the local tessdata folder (fails when using global...)
        tesseract.setLanguage(languages)            //by default we're reading English (TODO add more tessdata)

        tesseract
    }

    /**
      * Extracts text from the document using OCR. This method always succeeds but is more imprecise than directExtract
      *
      * @param document the docuement to do the OCR on
      * @return the text of the document
      */
    def ocrExtract(document: PDDocument): String = {
        /*
        https://sourceforge.net/p/tess4j/discussion/1202293/thread/4562eccb/

        Each thread needs its tesseract
         */

        val tesseract = getTesseract

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

        /*
    def dParsePDF(pdf: File): String = dParsePDF(new FileInputStream(pdf))

    def dParsePDF(pdf: InputStream): String = {
        val document = PDDocument.load(pdf)

        val renderer = new PDFRenderer(document)

        val temp = Pool.distributeIt(List.range(0, document.getNumberOfPages), (tuples: Iterable[(PDDocument, PDFRenderer, Int)]) => {
            val tesseract = getTesseract

            val future = Future[String] {     //start a future to do: OCR -> NLP -> ES
                tuples.foldLeft("")( (acc,tuple) => acc + threadSafeParsePage(tuple))
            }

            future.onComplete {             //when said future completes
                case Success(s: String) =>        //print success message
                    println("Pages " + tuples.head._3 + " to " + tuples.last._3 + " have been indexed")
                case Failure(e: Exception) =>   //otherwise exit if a problem occured
                    println("OCRParser has encountered a critical error.")
                    e.printStackTrace()
                    System.exit(1)
            }

            future

        }, 10)

        temp.toString()
    }

    def makeDistributable(document: PDDocument, renderer: PDFRenderer): ListBuffer[(PDDocument, PDFRenderer, Int)] = {
        val buffer = new ListBuffer[(PDDocument, PDFRenderer, Int)]

        for(i <- 0 until document.getNumberOfPages) buffer.append((document, renderer, i))

        buffer
    }*/
}