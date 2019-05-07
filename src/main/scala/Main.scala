import java.io.File


//https://github.com/overview/pdfocr

object Main {
    def main(args: Array[String]) {
        fixBug()

        val parser = new Parser
        val titleText = getFiles("./input/").map(parser.parsePDF)

        println(titleText)
        //TODO index the jsons in ES for Vincent
    }

    def fixBug(): Unit = {
        /*
        Bug on tesseract 4.0.0: crash when calling procedure is not tesseract itself. To fix, we're changing the locale
        to C using java's Native library (see CLibrary.java)

        https://github.com/nguyenq/tess4j/issues/106
         */

        CLibrary.INSTANCE.setlocale(CLibrary.LC_ALL, "C")
        CLibrary.INSTANCE.setlocale(CLibrary.LC_NUMERIC, "C")
        CLibrary.INSTANCE.setlocale(CLibrary.LC_CTYPE, "C")
    }

    /**
      * TODO TEMP change this to get all files from ES once
      * @param path
      * @return
      */
    def getFiles(path: String): List[File] = new File(path).listFiles().toList

}
