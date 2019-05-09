import java.io.File


//https://github.com/overview/pdfocr

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

        adminIndexFiles("./input/")

        //print(esIndexer.makeJson(temp2(0)))

        //println(titleText)
        //TODO index the jsons in ES for Vincent
    }

    def adminIndexFiles(path: String): Unit = {
        getFiles(path).foreach{ x =>

            val text = ocrParser.parsePDF(x)
            val wordTags = nlpParser.getTokenTags(text)

            esIndexer.bulkIndex(List(AdminFile(x.getName, text), AdminWord(x.getName, wordTags), AdminFileWord(x.getName, text, wordTags)))
        }
    }

    /**
      * TODO TEMP change this to get all files from ES once
      * @param path
      * @return
      */
    def getFiles(path: String): Array[File] = new File(path).listFiles()


}
