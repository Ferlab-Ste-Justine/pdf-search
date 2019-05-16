import java.net.{InetAddress, InetSocketAddress}

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.{JsNull, Json}

import scala.collection.JavaConverters._

/*
Inspired by https://github.com/kids-first/kf-portal-etl/blob/develop/kf-portal-etl-processors/src/test/scala/io/kf/etl/processors/download/transform/utils/EntityDataRetrieverTest.scala
 */

class URLIteratorTest extends FlatSpec with Matchers {
    /*
    "getNext" should "return ALLO" in {
        val result = URLIterator.getNext(Json.obj(
            "_links" -> Json.obj("next" -> "ALLO"),
            "name" -> "Watership Down",
            "location" -> Json.obj("lat" -> 51.235685, "long" -> -1.309197),
            "residents" -> Json.arr(
                Json.obj(
                    "name" -> "Fiver",
                    "age" -> 4,
                    "role" -> JsNull
                ),
                Json.obj(
                    "name" -> "Bigwig",
                    "age" -> 6,
                    "role" -> "Owsla"
                )
            )
        ))
        result shouldBe Some("ALLO")
    }

    it should "be None when no next" in {
        val result = URLIterator.getNext(Json.obj(
            "_links" -> Json.obj("pasnext" -> "ALLO"),
            "name" -> "Watership Down",
            "location" -> Json.obj("lat" -> 51.235685, "long" -> -1.309197),
            "residents" -> Json.arr(
                Json.obj(
                    "name" -> "Fiver",
                    "age" -> 4,
                    "role" -> JsNull
                ),
                Json.obj(
                    "name" -> "Bigwig",
                    "age" -> 6,
                    "role" -> "Owsla"
                )
            )
        ))
        result shouldBe None
    }

    it should "be None when no _links" in {
        val result = URLIterator.getNext(Json.obj(
            "name" -> "Watership Down",
            "location" -> Json.obj("lat" -> 51.235685, "long" -> -1.309197),
            "residents" -> Json.arr(
                Json.obj(
                    "name" -> "Fiver",
                    "age" -> 4,
                    "role" -> JsNull
                ),
                Json.obj(
                    "name" -> "Bigwig",
                    "age" -> 6,
                    "role" -> "Owsla"
                )
            )
        ))
        result shouldBe None
    }*/

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

            //println(start)
            val result: List[String] = URLIterator.applyOnAllFrom(start, "/studies", field = "name")(identity)
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

            val result: List[String] = URLIterator.applyOnAllFrom(start, "/studies", field = "name")(identity)
            result shouldBe List("Study 1", "Study 2")
        }
    }
}
