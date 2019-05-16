import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import play.api.libs.json.{JsObject, JsValue, Json}

import scala.annotation.tailrec

//https://stackoverflow.com/questions/1359689/how-to-send-http-request-in-java
object URLIterator {
    def applyOnAllFrom[B](start: String, mid: String, end: String, method: String = "GET", field: String = "external_id")(cont: String => B): List[B] = {

        @tailrec
        def getAllFrom(iter: String, resList: List[B]): List[B] = {

            def request = {
                val client = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder().uri(URI.create(start + iter + (if(resList.isEmpty) "?" else "&") + end)).build()

                val temp: HttpResponse[String] = client.send(request, BodyHandlers.ofString())

                temp.body()
            }

            val json= Json.parse(request)

            val arr = json("results").as[Array[JsObject]].map( _(field).as[String] )

            val fList = arr.foldLeft(resList)( (acc, ele) => {println(ele); acc :+ cont(ele)} )

            val next: Option[String] = (json \ "_links" \ "next").asOpt[String]

            next match {
                case Some(url) => print(url); getAllFrom(url, fList)
                case None => fList
            }
        }

        getAllFrom(mid, List())
    }

    //def getNameFromUrl(url: String): String =  url.substring(url.indexOf('/', url.indexOf('/')) + 1, url.length).replaceAll("(.pdf)$", "")

}
