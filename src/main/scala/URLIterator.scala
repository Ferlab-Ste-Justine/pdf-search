import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import play.api.libs.json.{JsObject, Json}

import scala.annotation.tailrec

//https://stackoverflow.com/questions/1359689/how-to-send-http-request-in-java
object URLIterator {

    //https://stackoverflow.com/questions/3508077/how-to-define-type-disjunction-union-types
    //https://www.scala-lang.org/api/2.12.1/scala/PartialFunction.html
    sealed abstract class StraightOrBatched[T, D] extends (T => D)
    object StraightOrBatched {
        implicit class StraightWitness[B](cont: List[String] => B) extends StraightOrBatched[List[String], B] {
            override def apply(v1: List[String]): B = cont(v1)
        }
        implicit class BatchWitness[B](cont: List[List[String]] => List[B]) extends StraightOrBatched[List[List[String]], List[B]] {
            override def apply(v1: List[List[String]]): List[B] = cont(v1)
        }
    }

    def applyOnAllFrom[B](start: String, mid: String, end: String = "", fields: List[String], method: String = "GET", batched: Boolean = false)(cont: List[String] => B): List[B] = {

        @tailrec
        def getAllFrom(iter: String, resList: List[B]): List[B] = {

            def request = {
                val client = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder().uri(URI.create(start + iter + (if(resList.isEmpty) "?" else "&") + end)).build()

                val temp: HttpResponse[String] = client.send(request, BodyHandlers.ofString())

                temp.body()
            }

            val json= Json.parse(request)

            val fList = if(!batched) {
                json("results").as[Array[JsObject]].foldLeft(resList){ (acc, jsonObj: JsObject) =>
                    val temp = fields.map{ item =>
                        jsonObj(item).asOpt[String] match {
                            case Some(value) => value
                            case None => "null"
                        }
                    }

                    acc :+ cont(temp)
                }
            } else {
                val linear: List[String] = json("results").as[Array[JsObject]].foldLeft(List[String]()){ (acc, jsonObj: JsObject) =>
                    acc ::: fields.map{ item =>
                        jsonObj(item).asOpt[String] match {
                            case Some(value) => value
                            case None => "null"
                        }
                    }
                }

                resList :+ cont(linear)
            }

            val next: Option[String] = (json \ "_links" \ "next").asOpt[String]

            next match {
                case Some(url) => print(url); getAllFrom(url, fList)
                case None => fList
            }
        }

        getAllFrom(mid, List())
    }

    /*
    def batchedApplyOnAllFrom[B](start: String, mid: String, end: String = "", fields: List[String], method: String = "GET")(cont: List[List[String]] => List[B]): List[B] = {

    }*/

    //def getNameFromUrl(url: String): String =  url.substring(url.indexOf('/', url.indexOf('/')) + 1, url.length).replaceAll("(.pdf)$", "")

}
