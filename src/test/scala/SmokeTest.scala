import java.io.File

import org.scalatest.{FlatSpec, Matchers}

class SmokeTest extends FlatSpec with Matchers {
    val ocrParser = new OCRParser
    val nlpParser = new NLPParser

    "ocr -> nlp" should "return [Brain cancer sucks man] with input ./testInput/brainCancer.pdf" in {
        val result = nlpParser.getNouns(ocrParser.parsePDF(new File("./testInput/brainCancer.pdf")))
        result shouldBe List("Brain", "cancer", "sucks", "man")
    }

    "ocr -> nlp" should "return [Yoda he's lightsaber] with input ./testInput/yoda.pdf" in {
        val result = nlpParser.getNouns(ocrParser.parsePDF(new File("./testInput/yoda.pdf")))
        result shouldBe List("Yoda", "he's", "lightsabers")  //note: he's should probably not be here, but it's ML...
    }

    //TODO more tests as I devellop system
}
