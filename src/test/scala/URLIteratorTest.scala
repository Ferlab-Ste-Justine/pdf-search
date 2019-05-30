import java.net.{InetAddress, InetSocketAddress}

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.{JsNull, Json}

import scala.collection.JavaConverters._

/*
Inspired by https://github.com/kids-first/kf-portal-etl/blob/develop/kf-portal-etl-processors/src/test/scala/io/kf/etl/processors/download/transform/utils/EntityDataRetrieverTest.scala
 */

class URLIteratorTest extends FlatSpec with Matchers {

    "apply" should "return the correct data" in {
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

            val result = URLIterator.applyOnAllFrom(start, "/studies", fields = List("name"))(identity).flatten
            result shouldBe List("Study 1", "Study 2")
        }
    }

    it should "return the correct data with recursion" in {
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
              |            "kf_id": "1",
              |            "name": "Study 1"
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
              |            "kf_id": "2",
              |            "name": "Study 2"
              |        }
              | ],
              | "total":2
              |}
            """.stripMargin)

        DataService.withDataService(Map(
            "/studies" -> handlerPage1,
            "/studies2" -> handlerPage2
        )) { start =>

            /*
            val result: Seq[Map[String, String]] = URLIterator.applyOnAllFrom(start, "/studies", fields = List("name"))(identity).foldLeft(List[String]()) { (acc, m: Map[String, String]) =>
            }
            result shouldBe List("Study 1", "Study 2")*/
        }
    }
}
