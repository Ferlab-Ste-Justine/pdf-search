import java.io.File

import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}
import play.api.libs.json.Json

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/*
class SmokeTest extends FlatSpec with Matchers with PrivateMethodTester {
  val ocrParser = new OCRParser
  val nlpParser = new NLPParser
  val esIndexer = new ESIndexer

  "ocr -> nlp" should "return [Brain cancer sucks man] with input ./testInput/brainCancer.pdf" in {
    val result = nlpParser.getLemmas(ocrParser.parsePDF(new File("./testInput/brainCancer.pdf")))
    result shouldBe Set("brain", "cancer", "fun", "man")
  }

  it should "return [Yoda he's lightsaber] with input ./testInput/yoda.pdf" in {
    val result = nlpParser.getLemmas(ocrParser.parsePDF(new File("./testInput/yoda.pdf")))
    result shouldBe Set("yoda", "lightsabers")
  }

  "urliter -> Model reads" should "return build correct json" in {

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
        |            "name": "Yoda ",
        |            "name2": "I wish "
        |        },
        |        {
        |            "name": "is ",
        |            "name2": "I was "
        |        },
        |        {
        |            "name": "amazing, ",
        |            "name2": "as cool "
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
        |            "name": "he's ",
        |            "name2": "as him!"
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

      val results = Await.result(URLIterator.fetchGeneric(start, "/studies", fields = List("name", "name2")), Duration.Inf)

      val nameList: List[String] = List("Yoda ", "is ", "amazing, ", "he's ", "just so great ", "with ", "lightsabers!")

      val name2List = List("I wish ", "I was ", "as cool ", "as him!")

      val testers: List[(Option[String], Option[String])] = nameList.map(Some(_)).zipAll(name2List.map(Some(_)), None, None)

      results shouldBe testers.map((b: (Option[String], Option[String])) => Holder(List(b._1, b._2)))

      val asString = results.foldLeft(("", "")) { (acc, tester: Holder) =>

        (acc._1 + tester.valList.head.getOrElse(""), acc._2 + tester.valList(1).getOrElse(""))
      }.productIterator.toList.mkString(" ")

      val asJson = IndexingRequest("id", asString, None, List("this", "is a List"), None, None).toJson

      asJson shouldBe Json.obj(
        "kf_id" -> "id",
        "text" -> "Yoda is amazing, he's just so great with lightsabers! I wish I was as cool as him!",
        "words" -> Json.arr("this", "is a List")
      ).toString()

      succeed
    }
  }
}
*/