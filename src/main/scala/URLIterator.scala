import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import play.api.libs.json.{JsObject, JsValue, Json}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

//https://stackoverflow.com/questions/1359689/how-to-send-http-request-in-java
object URLIterator {

    //https://stackoverflow.com/questions/3508077/how-to-define-type-disjunction-union-types
    //https://www.scala-lang.org/api/2.12.1/scala/PartialFunction.html

    def applyOnAllFrom[B](start: String, mid: String, end: String = "", fields: List[String], links: List[String] = List(), method: String = "GET", batched: Boolean = false)(cont: List[String] => B): List[B] = {

        @tailrec
        def getAllFrom(iter: String, resList: List[B]): List[B] = {
            val client = HttpClient.newHttpClient()

            def request = {
                val request = HttpRequest.newBuilder().uri(URI.create(start + iter + (if(resList.isEmpty) "?" else "&") + end)).build()

                val response: HttpResponse[String] = client.send(request, BodyHandlers.ofString())

                response.body()
            }

            def extractAll(obj: JsObject): List[String] = {
                val objLinks = obj("_links").as[JsObject]

                def extract(thisObj: JsObject, item: String) = {
                    thisObj(item).asOpt[String] match {
                        case Some(value) => value
                        case None => "null"
                    }
                }

                fields.map(extract(obj, _)) ::: links.map(extract(objLinks, _))
            }

            val json= Json.parse(request)

            val results: Array[JsObject] = json("results").as[Array[JsObject]]

            val fList = if(!batched) {  //if we're not batching, simply call the cont on the requested fields and append the result to our resList
                results.foldLeft(resList){ (acc, jsonObj: JsObject) =>
                    acc :+ cont(extractAll(jsonObj))
                }
            } else {    //if we're batching, extract the requested fields into a List[List[String]] and call the cont on that
                val linear: List[String] = results.foldLeft(List[String]()){ (acc, jsonObj: JsObject) =>
                    acc ::: extractAll(jsonObj)
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

    def batchedApplyOnAllFrom[C](start: String, mid: String, end: String = "", fields: List[String], links: List[String] = List(), method: String = "GET")(cont: List[List[String]] => C): List[C] = {
        applyOnAllFrom(start, mid, end, fields, links, method = method, batched = true) { batchedfield: List[String] =>
            cont(batchedfield.grouped(fields.size).toList)
        }
    }

    //def getNameFromUrl(url: String): String =  url.substring(url.indexOf('/', url.indexOf('/')) + 1, url.length).replaceAll("(.pdf)$", "")

}
