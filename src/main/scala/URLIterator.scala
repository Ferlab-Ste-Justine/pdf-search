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

    /**
      * Calls an Dataservice URL and iterates on it, applying a callback "cont" on every result in the returned JSON.
      *
      * Cont can be a Future, and the iteration will continue even while cont does it's thing.
      *
      * The Map passed to cont has all the requested links and fields. The fields will be directly accessible using
      * map("FIELD_NAME"). The links have a header added and can be accessed with map("_links.LINK_NAME").
      *
      * @param start the start of the URL
      * @param mid the middle of the URL
      * @param end the end of the URL
      * @param fields the requested fields
      * @param links the requested links
      * @param method the HTTP method to use
      * @param cont the continuation
      * @tparam B the return type of Cont
      * @return the list of all the results of the calls to cont
      */
    def applyOnAllFrom[B](start: String, mid: String, end: String = "", fields: List[String], links: List[String] = List(), method: String = "GET", retries: Int = 10)(cont: Map[String, String] => B = identity[Map[String, String]] _ ): List[B] = {
        val client = HttpClient.newHttpClient()

        /**
          * Gets everything from the start-mid-end URL, calling cont on it and accumulating the calls' results into
          * resList
          *
          * @param iter the current middle part of the URL
          * @param resList the result list
          * @return the completed result list
          */
        @tailrec
        def getAllFrom(iter: String, resList: List[B]): List[B] = {

            /**
              * Sends a request
              *
              * @return the response as a String
              */
            def request = {

                /**
                  * Sends the request retries times
                  *
                  * @param tries the current number of tries
                  * @return the response body as a String
                  */
                @tailrec
                def requestIter(tries: Int = 0): String = {
                    try {
                        val request = HttpRequest.newBuilder().uri(URI.create(start + iter + (if(resList.isEmpty) "?" else "&") + end)).build()

                        val response: HttpResponse[String] = client.send(request, BodyHandlers.ofString())

                        response.body()
                    } catch {
                        case e: Exception =>
                            if(tries >= retries) {
                                //TODO que faire lorsqu'on plante? demander Jeremy
                                val exception: Exception = new Exception(s"Retrying failed $tries times. Exiting now...")
                                exception.initCause(e.getCause)
                                exception.setStackTrace(e.getStackTrace)
                                exception.printStackTrace()

                                System.exit(1)
                            }
                            requestIter(tries+1)
                    }
                }

                requestIter()
            }

            /**
              * Transforms the obj into a Map of the requested links of fields
              *
              * @param obj the object
              * @return the corresponding map
              */
            def intoMap(obj: JsObject): Map[String, String] = {
                def extract(thisObj: JsObject, item: String, prefix: String = ""): (String, String) = {
                    (prefix+item, thisObj(item).asOpt[String] match {
                        case Some(value) => value
                        case None => "null"
                    })
                }

                val fieldMap = fields.map(extract(obj, _))
                val linkMap = //if we're requesting links, ask for them. Otherwise, return no Link tuples
                    if(links.nonEmpty) links.map(extract(obj("_links").as[JsObject], _, "_links."))
                    else List()

                (fieldMap ::: linkMap).toMap    //transforms the list of tuples into a Map
            }

            val json= Json.parse(request)

            val fList: List[B] =    //some resultsets are a direct JSON object and not an Array
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
                case None => fList  //if there's no next, we're done!
            }
        }

        getAllFrom(mid, List())
    }

    /**
      * Batching version of applyOnAllFrom
      *
      * Uses a mutable list to accumulate Maps from a call to the non-batched method. When that accumulator reaches
      * batchSize, it calls cont on it and resets the accumulator.
      *
      * Cont can create a Future. That way, the accumulation will continue even during the call to cont.
      *
      * @param start the start of the URL
      * @param mid the middle of the URL (starting position)
      * @param end the end of the URL
      * @param fields the requested fields
      * @param links the requested links
      * @param method the HTTP method to use
      * @param batchSize the batchsize
      * @param cont the continuation to call on the batch
      * @tparam C the return type of Cont
      * @return a list of all the results of cont
      */
    def batchedApplyOnAllFrom[C](start: String, mid: String, end: String = "", fields: List[String], links: List[String] = List(), method: String = "GET", retries: Int = 10, batchSize: Int = 500)(cont: List[Map[String, String]] => C): List[C] = {
        var accumulator = ListBuffer[Map[String, String]]()
        val results = ListBuffer[C]()

        /**
          * Applies the continuation on batch.toList
          *
          * @param batch the batch
          * @return the result of the continuation
          */
        def applyCont(batch: ListBuffer[Map[String, String]]) = results += cont(batch.toList)

        applyOnAllFrom(start, mid, end, fields, links, method = method) { item: Map[String, String] =>
            accumulator += item
            if(accumulator.size >= batchSize) {
                applyCont(accumulator)
                accumulator = ListBuffer[Map[String, String]]()
            }
        }

        if(accumulator.nonEmpty) applyCont(accumulator)

        results.toList
    }
}
