import java.io.File

import org.elasticsearch.common.Strings
import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class SmokeTest extends FlatSpec with Matchers with PrivateMethodTester {
    val ocrParser = new OCRParser
    val nlpParser = new NLPParser
    val esIndexer = new ESIndexer
    //val publicMakeJson: PrivateMethod[Array[String]] = PrivateMethod[Array[String]]('makeJson)


    "ocr -> nlp" should "return [Brain cancer sucks man] with input ./testInput/brainCancer.pdf" in {
        val result = nlpParser.getLemmas(ocrParser.parsePDF(new File("./testInput/brainCancer.pdf")))
        result shouldBe Set("brain", "cancer", "fun", "man")
    }

    it should "return [Yoda he's lightsaber] with input ./testInput/yoda.pdf" in {
        val result = nlpParser.getLemmas(ocrParser.parsePDF(new File("./testInput/yoda.pdf")))
        result shouldBe Set("yoda", "lightsabers")
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

            val result = URLIterator.applyOnAllFrom(start, "/studies", fields = List("name"))(identity).flatten.foldLeft("")( (acc, ele) => acc + ele)
            result shouldBe "Yoda is amazing, he's just so great with lightsabers!"

            val temp = IndexingRequest("yoda", result, nlpParser.getLemmas(result))

            val json = Strings.toString(esIndexer.makeJson(temp))

            json shouldBe s"""
               |{"title":"yoda",
               |"text":"Yoda is amazing, he's just so great with lightsabers!",
               |"type":"local",
               |"words":
               |["yoda","lightsabers"]
               |}""".stripMargin.replaceAll("\n", "")   //realllllly doesn't like newlines...*/
        }
    }
}
