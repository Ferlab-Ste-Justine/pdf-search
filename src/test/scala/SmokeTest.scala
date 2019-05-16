import java.io.File

import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}

class SmokeTest extends FlatSpec with Matchers with PrivateMethodTester {
    val ocrParser = new OCRParser
    val nlpParser = new NLPParser
    val esIndexer = new ESIndexer
    //val publicMakeJson: PrivateMethod[Array[String]] = PrivateMethod[Array[String]]('makeJson)

    "ocr -> nlp" should "return [Brain cancer sucks man] with input ./testInput/brainCancer.pdf" in {
        val result = nlpParser.getNouns(ocrParser.parsePDF(new File("./testInput/brainCancer.pdf")))
        result shouldBe Array("Brain", "cancer", "fun", "man", "")
    }

    it should "return [Yoda he's lightsaber] with input ./testInput/yoda.pdf" in {
        val result = nlpParser.getNouns(ocrParser.parsePDF(new File("./testInput/yoda.pdf")))
        result shouldBe Array("Yoda", "lightsabers")  //note: he's should probably not be here, but it's ML...
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

            val temp = AdminFileWord("yoda", result, tokenTags)

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
                   |{"word":"!","tag":"."}]}""".stripMargin.replaceAll("\n", ""))   //realllllly doesn't like newlines...
        }
    }

    /* TODO temp refactoring AdminFileWordKeyword
    "ocr -> nlp -> es.makeJson" should "return [Yoda he's lightsaber] with input ./testInput/yoda.pdf" in {
        val text = ocrParser.parsePDF(new File("./testInput/yoda.pdf")).replace('\n', ' ')
        //the shouldBe has trouble with \n, so we replace it with ' '

        val tokenTags = nlpParser.getTokenTags(text)

        val result = esIndexer invokePrivate publicMakeJson(AdminFileWord("yoda", text, tokenTags))

        result shouldBe Array(
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
    }*/
}
