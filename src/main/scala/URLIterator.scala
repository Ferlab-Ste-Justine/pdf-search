import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import play.api.libs.json.{JsObject, JsValue, Json}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, _}

//https://stackoverflow.com/questions/1359689/how-to-send-http-request-in-java
object URLIterator {

    //https://stackoverflow.com/questions/3508077/how-to-define-type-disjunction-union-types
    //https://www.scala-lang.org/api/2.12.1/scala/PartialFunction.html

    def applyOnAllFrom[B](start: String, mid: String, end: String = "", fields: List[String], links: List[String] = List(), method: String = "GET")(cont: Map[String, String] => B): List[B] = {
        val client = HttpClient.newHttpClient()

        @tailrec
        def getAllFrom(iter: String, resList: List[B]): List[B] = {

            def request = {
                val request = HttpRequest.newBuilder().uri(URI.create(start + iter + (if(resList.isEmpty) "?" else "&") + end)).build()

                val response: HttpResponse[String] = client.send(request, BodyHandlers.ofString())

                response.body()
            }

            def intoMap(obj: JsObject): Map[String, String] = {
                def extract(thisObj: JsObject, item: String, prefix: String = ""): (String, String) = {
                    (prefix+item, thisObj(item).asOpt[String] match {
                        case Some(value) => value
                        case None => "null"
                    })
                }

                val fieldMap = fields.map(extract(obj, _))
                val linkMap = if(links.nonEmpty) links.map(extract(obj("_links").as[JsObject], _, "_links.")) else List()

                (fieldMap ::: linkMap).toMap
            }

            val json= Json.parse(request)

            val fList: List[B] =
                try {
                    val results: Array[JsObject] = json("results").as[Array[JsObject]]

                    results.foldLeft(resList)( (acc, jsonObj: JsObject) => acc :+ cont(intoMap(jsonObj)) )

                } catch {
                    case e: play.api.libs.json.JsResultException =>
                        val results: JsObject = json("results").as[JsObject]

                        resList :+ cont(intoMap(results))
                }



            val next: Option[String] = (json \ "_links" \ "next").asOpt[String]

            next match {
                case Some(url) => println(url); getAllFrom(url, fList)
                case None => fList
            }
        }

        getAllFrom(mid, List())
    }

    def batchedApplyOnAllFrom[C](start: String, mid: String, end: String = "", fields: List[String], links: List[String] = List(), method: String = "GET", batchSize: Int = 500)(cont: List[Map[String, String]] => C): List[C] = {
        var accumulator = ListBuffer[Map[String, String]]()
        val results = ListBuffer[C]()

        def applyCont(batch: ListBuffer[Map[String, String]]) = cont(batch.toList)

        applyOnAllFrom(start, mid, end, fields, links, method = method) { item: Map[String, String] =>
            accumulator += item
            if(accumulator.size >= batchSize) {
                results += applyCont(accumulator)
                accumulator = ListBuffer[Map[String, String]]()
            }
        }

        if(accumulator.nonEmpty) results += applyCont(accumulator)

        results.toList
    }

    //def getNameFromUrl(url: String): String =  url.substring(url.indexOf('/', url.indexOf('/')) + 1, url.length).replaceAll("(.pdf)$", "")

}
