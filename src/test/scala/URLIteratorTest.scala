import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/*
Inspired by https://github.com/kids-first/kf-portal-etl/blob/develop/kf-portal-etl-processors/src/test/scala/io/kf/etl/processors/download/transform/utils/EntityDataRetrieverTest.scala
 */

class URLIteratorTest extends FlatSpec with Matchers {

  "fetchGeneric" should "return the correct data" in {
    DataService.withDataService(Map("/studies" -> jsonHandler(
      """
        |{
        |    "_links": {
        |        "self": "/studies"
        |    },
        |    "_status": {
        |        "code": 200,
        |        "message": "success"
        |    },
        |    "limit": 10,
        |    "results": [
        |        {
        |            "kf_id": "1",
        |            "name": "Study 1"
        |        },
        |        {
        |            "kf_id": "2",
        |            "name": "Study 2"
        |        }
        | ],
        | "total":2
        |}
      """.stripMargin))) { start =>
      val result = Await.result(URLIterator.fetchGeneric(start, "/studies", fields = List("name")), Duration.Inf)

      result shouldBe List(Holder(List(Some("Study 1"))), Holder(List(Some("Study 2"))))
    }
  }

  "fetchCont" should "return the correct data with recursion" in {
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
        |            "_links": {
        |                "family": "/families/THE_FAMILY1"
        |            },
        |            "ethnicity": "THE_ETHNICITY1",
        |            "external_id": "THE_EXTERNAL_ID1",
        |            "gender": "THE_GENDER1",
        |            "kf_id": "THE_KF_ID1",
        |            "race": "THE_RACE1"
        |        }
        | ],
        | "total":2
        |}
      """.stripMargin)
    val handlerPage2 = jsonHandler(
      """
        |{
        |    "_links": {
        |        "self": "/studies2"
        |    },
        |    "_status": {
        |        "code": 200,
        |        "message": "success"
        |    },
        |    "limit": 1,
        |    "results": [
        |        {
        |            "_links": {
        |                "family": "/families/THE_FAMILY2"
        |            },
        |            "ethnicity": "THE_ETHNICITY2",
        |            "external_id": "THE_EXTERNAL_ID2",
        |            "gender": "THE_GENDER2",
        |            "kf_id": "THE_KF_ID2",
        |            "race": "THE_RACE2"
        |        }
        | ],
        | "total":2
        |}
      """.stripMargin)

    DataService.withDataService(Map(
      "/studies" -> handlerPage1,
      "/studies2" -> handlerPage2
    )) { start =>

      import Model.ExternalImplicits._
      val result = URLIterator.fetchWithCont(start, "/studies") { p: Participant =>
        p.ethnicity.get
      }

      Await.result(result, Duration.Inf).head shouldBe "THE_ETHNICITY1"
    }
  }
}
