import java.io.File

import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}

class SmokeTest extends FlatSpec with Matchers with PrivateMethodTester {
    val ocrParser = new OCRParser
    val nlpParser = new NLPParser
    val esIndexer = new ESIndexer
    val publicMakeJson: PrivateMethod[List[String]] = PrivateMethod[List[String]]('makeJson)

    "ocr -> nlp" should "return [Brain cancer sucks man] with input ./testInput/brainCancer.pdf" in {
        val result = nlpParser.getNouns(ocrParser.parsePDF(new File("./testInput/brainCancer.pdf")))
        result shouldBe List("Brain", "cancer", "sucks", "man")
    }

    "ocr -> nlp" should "return [Yoda he's lightsaber] with input ./testInput/yoda.pdf" in {
        val result = nlpParser.getNouns(ocrParser.parsePDF(new File("./testInput/yoda.pdf")))
        result shouldBe List("Yoda", "he's", "lightsabers")  //note: he's should probably not be here, but it's ML...
    }

    "ocr -> nlp -> es.makeJson" should "return [Yoda he's lightsaber] with input ./testInput/yoda.pdf" in {
        val text = ocrParser.parsePDF(new File("./testInput/yoda.pdf")).replace('\n', ' ')
        //the shouldBe has trouble with \n, so we replace it with ' '

        val tokenTags = nlpParser.getTokenTags(text)

        val result = esIndexer invokePrivate publicMakeJson(AdminFileWord("yoda", text, tokenTags))

        result shouldBe List(
            s"""
              |{"title":"yoda",
              |"text":"$text",
              |"words":
                  |[{"word":"Yoda","tag":"NNP"},
                  |{"word":"is","tag":"VBZ"},
                  |{"word":"amazing,","tag":"JJ"},
                  |{"word":"he's","tag":"NN"},
                  |{"word":"just","tag":"RB"},
                  |{"word":"so","tag":"RB"},
                  |{"word":"great","tag":"JJ"},
                  |{"word":"with","tag":"IN"},
                  |{"word":"lightsabers!","tag":"NN"}]
              |}""".stripMargin.replaceAll("\n", ""))   //realllllly doesn't like newlines...
    }
}
