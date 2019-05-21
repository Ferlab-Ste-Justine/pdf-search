import java.io.File

import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}

class SmokeTest extends FlatSpec with Matchers with PrivateMethodTester {
    val ocrParser = new OCRParser
    val nlpParser = new NLPParser
    val esIndexer = new ESIndexer
    //val publicMakeJson: PrivateMethod[Array[String]] = PrivateMethod[Array[String]]('makeJson)

    "ocr -> nlp" should "return [Brain cancer sucks man] with input ./testInput/brainCancer.pdf" in {
        val result = nlpParser.getTokenTags(ocrParser.parsePDF(new File("./testInput/brainCancer.pdf")))
        result shouldBe Array(("\"","``"), ("Brain","NN"), ("cancer","NN"), ("is","VBZ"), ("bad","JJ"), (".","."), ("This","DT"), ("sucks","VBZ"), (":",":"), ("it","PRP"), ("is","VBZ"), ("not","RB"), ("fun","NN"), (",",","), ("man","NN"), ("","FW"), (".","."), ("\"","''"))
    }

    it should "return [Yoda he's lightsaber] with input ./testInput/yoda.pdf" in {
        val result = nlpParser.getTokenTags(ocrParser.parsePDF(new File("./testInput/yoda.pdf")))
        result shouldBe Array(("Yoda","NNP"), ("is","VBZ"), ("amazing","JJ"), (",",","), ("he","PRP"), ("'s","VBZ"), ("just","RB"), ("so","RB"), ("great","JJ"), ("with","IN"), ("lightsabers","NNS"), ("!","."))
    }

    "urliter -> nlp -> es.makeJson" should "return build correct json" in {

        val handlerPage1 = jsonHandler(
            """
              |{
              |    "_links": {
              |        "self": "/studies",
              |        "next": "/studies2"
              |    },
              |    "_status": {
              |        "code": 200,
              |        "message": "success"
              |    },
              |    "limit": 1,
              |    "results": [
              |        {
              |            "name": "Yoda "
              |        },
              |        {
              |            "name": "is "
              |        },
              |        {
              |            "name": "amazing, "
              |        }
              | ],
              | "total":2
              |}
            """.stripMargin)
        val handlerPage2 = jsonHandler(
            """
              |{
              |    "_links": {
              |        "next": "/studies3",
              |        "self": "/studies2"
              |    },
              |    "_status": {
              |        "code": 200,
              |        "message": "success"
              |    },
              |    "limit": 1,
              |    "results": [
              |        {
              |            "name": "he's "
              |        },
              |        {
              |            "name": "just so great "
              |        }
              | ],
              | "total":2
              |}
            """.stripMargin)
        val handlerPage3 = jsonHandler(
            """
              |{
              |    "_links": {
              |        "self": "/studies3"
              |    },
              |    "_status": {
              |        "code": 200,
              |        "message": "success"
              |    },
              |    "limit": 1,
              |    "results": [
              |        {
              |            "name": "with "
              |        },
              |        {
              |            "name": "lightsabers!"
              |        }
              | ],
              | "total":2
              |}
            """.stripMargin)

        DataService.withDataService(Map(
            "/studies" -> handlerPage1,
            "/studies2" -> handlerPage2,
            "/studies3" -> handlerPage3
        )) { start =>

            val result = URLIterator.applyOnAllFrom(start, "/studies", field = "name")(identity).mkString
            result shouldBe "Yoda is amazing, he's just so great with lightsabers!"

            val tokenTags = nlpParser.getTokenTags(result)

            val temp = AdminFileWordLemmas("yoda", result, tokenTags, nlpParser.getLemmas(result))

            val json = esIndexer.makeJson(temp)

            json shouldBe Array(
                s"""
                   |{"title":"yoda",
                   |"text":"Yoda is amazing, he's just so great with lightsabers!",
                   |"words":
                   |[{"word":"Yoda","tag":"NNP"},
                   |{"word":"is","tag":"VBZ"},
                   |{"word":"amazing","tag":"JJ"},
                   |{"word":",","tag":","},
                   |{"word":"he","tag":"PRP"},
                   |{"word":"'s","tag":"VBZ"},
                   |{"word":"just","tag":"RB"},
                   |{"word":"so","tag":"RB"},
                   |{"word":"great","tag":"JJ"},
                   |{"word":"with","tag":"IN"},
                   |{"word":"lightsabers","tag":"NNS"},
                   |{"word":"!","tag":"."}],
                   |"lemmas":
                   |[{"lemma":"yoda"},
                   |{"lemma":"lightsabers"}]}""".stripMargin.replaceAll("\n", ""))   //realllllly doesn't like newlines...
        }
    }
}
