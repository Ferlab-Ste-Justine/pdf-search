import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.json.{JsObject, JsValue, Json, Reads}
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import play.api.libs.ws.JsonBodyReadables._

//https://stackoverflow.com/questions/1359689/how-to-send-http-request-in-java
object URLIterator {
  //https://stackoverflow.com/questions/3508077/how-to-define-type-disjunction-union-types
  //https://www.scala-lang.org/api/2.12.1/scala/PartialFunction.html

  /**
    * Batching version of applyOnAllFrom
    *
    * Uses a mutable list to accumulate Maps from a call to the non-batched method. When that accumulator reaches
    * batchSize, it calls cont on it and resets the accumulator.
    *
    * Cont can create a Future. That way, the accumulation will continue even during the call to cont.
    *
    * @param start     the start of the URL
    * @param mid       the middle of the URL (starting position)
    * @param end       the end of the URL
    * @param fields    the requested fields
    * @param links     the requested links
    * @param method    the HTTP method to use
    * @param batchSize the batchsize
    * @param cont      the continuation to call on the batch
    * @tparam C the return type of Cont
    * @return a list of all the results of cont
    */
  def blockingFetchBatchedcont[C](start: String, mid: String, end: String = "", fields: List[String], links: List[String] = List(), method: String = "GET", retries: Int = 10, batchSize: Int = 500)(cont: List[Map[String, String]] => C): List[C] = {
    var accumulator = ListBuffer[Map[String, String]]()
    val results = ListBuffer[C]()

    /**
      * Applies the continuation on batch.toList
      *
      * @param batch the batch
      * @return the result of the continuation
      */
    def applyCont(batch: ListBuffer[Map[String, String]]) = results += cont(batch.toList)

    blockingFetch(start, mid, end, fields, links, method = method) { item: Map[String, String] =>
      accumulator += item
      if (accumulator.size >= batchSize) {
        applyCont(accumulator)
        accumulator = ListBuffer[Map[String, String]]()
      }
    }
    if (accumulator.nonEmpty) applyCont(accumulator)
    results.toList
  }

  /**
    * Calls an Dataservice URL and iterates on it, applying a callback "cont" on every result in the returned JSON.
    *
    * Cont can be a Future, and the iteration will continue even while cont does it's thing.
    *
    * The Map passed to cont has all the requested links and fields. The fields will be directly accessible using
    * map("FIELD_NAME"). The links have a header added and can be accessed with map("_links.LINK_NAME").
    *
    * @param start  the start of the URL
    * @param mid    the middle of the URL
    * @param end    the end of the URL
    * @param fields the requested fields
    * @param links  the requested links
    * @param method the HTTP method to use
    * @param cont   the continuation
    * @tparam B the return type of Cont
    * @return the list of all the results of the calls to cont
    */
  def blockingFetch[B](start: String, mid: String, end: String = "", fields: List[String], links: List[String] = List(), method: String = "GET", retries: Int = 10)(cont: Map[String, String] => B = identity[Map[String, String]] _): List[B] = {
    val client = HttpClient.newHttpClient()

    /**
      * Gets everything from the start-mid-end URL, calling cont on it and accumulating the calls' results into
      * resList
      *
      * @param iter    the current middle part of the URL
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
            val request = HttpRequest.newBuilder().uri(URI.create(start + iter + (if (resList.isEmpty) "?" else "&") + end)).build()
            val response: HttpResponse[String] = client.send(request, BodyHandlers.ofString())
            response.body()
          } catch {
            case e: Exception =>
              if (tries >= retries) {
                //TODO que faire lorsqu'on plante? demander Jeremy
                val exception: Exception = new Exception(s"Retrying failed $tries times. Exiting now...")
                exception.initCause(e.getCause)
                exception.setStackTrace(e.getStackTrace)
                exception.printStackTrace()
                System.exit(1)
              }
              requestIter(tries + 1)
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
          (prefix + item, thisObj(item).asOpt[String] match {
            case Some(value) => value
            case None => "null"
          })
        }

        val fieldMap = fields.map(extract(obj, _))
        val linkMap = //if we're requesting links, ask for them. Otherwise, return no Link tuples
          if (links.nonEmpty) links.map(extract(obj("_links").as[JsObject], _, "_links."))
          else List()
        (fieldMap ::: linkMap).toMap //transforms the list of tuples into a Map
      }

      val json = Json.parse(request)
      val fList: List[B] = //some resultsets are a direct JSON object and not an Array
        try {
          val results: Array[JsObject] = json("results").as[Array[JsObject]]
          results.foldLeft(resList)((acc, jsonObj: JsObject) => acc :+ cont(intoMap(jsonObj)))
        } catch {
          case e: play.api.libs.json.JsResultException =>
            val results: JsObject = json("results").as[JsObject]
            resList :+ cont(intoMap(results))
        }
      val next: Option[String] = (json \ "_links" \ "next").asOpt[String]
      next match {
        case Some(url) => println(url); getAllFrom(url, fList)
        case None => fList //if there's no next, we're done!
      }
    }

    getAllFrom(mid, List())
  }

  sealed case class Util[B <: Model](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10, reader: Reads[B]) {
    implicit val system: ActorSystem = ActorSystem()
    system.registerOnTermination {
      System.exit(0)
    }

    implicit val materializer: ActorMaterializer = ActorMaterializer()
    // Create the standalone WS client
    // no argument defaults to a AhcWSClientConfig created from
    // "AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)"

    implicit val r = reader

    val client = StandaloneAhcWSClient()

    /**
      * Sends a request
      *
      * @return the response as a String
      */
    def request(iter:String, first: Boolean, tries: Int = 0): Future[JsValue] = {
      def requestIter(tries: Int): Future[JsValue] = {
        if (tries >= retries) throw new IllegalArgumentException
        val temp: Future[StandaloneWSRequest#Response] = client.url(start + iter + (if (first) "?" else "&") + end).get()
        temp.flatMap { resp =>
          val code = resp.status
          if (code < 200 || code >= 300) requestIter(tries + 1)
          else Future(resp.body[JsValue])
        }
      }

      requestIter(0)
    }

    def fetchWithCont[A](cont: B => A): Future[List[A]] = {  //wrapper allors us to not pass cont
      def singler(iter: String, resList: List[A]): Future[List[A]] = {
        request(iter, resList.isEmpty).flatMap { json =>
          val results: Seq[JsValue] = json("results").as[Array[JsObject]]

          val models = resList ++ results.map( result => cont(result.as[B]) )

          val next: Option[String] = (json \ "_links" \ "next").asOpt[String]

          next match {
            case Some(url) => singler(url, models)
            case None => Future.successful(models)
          }
        }
      }

      singler(mid, List())
    }

    def fetch: Future[List[B]] = fetchWithCont(identity)

    def fetchWithBatchedcont[A](batchedCont: List[B] => List[A], batchSize: Int = 1500): Future[List[A]] = {
      def batcher(iter: String, builder: List[B], resList: List[A]): Future[List[A]] = {
        request(iter, resList.isEmpty).flatMap { json =>
          val results: Seq[JsValue] = json("results").as[Array[JsObject]]

          val batchResult = ListBuffer[A]() ++= resList

          val accumulator = results.foldLeft(builder) { (acc: List[B], result) =>
            val asModel = result.as[B]

            if(acc.length >= batchSize) {
              batchResult ++= batchedCont(acc)
              List(asModel)
            } else {
              acc :+ asModel
            }

          }

          val next: Option[String] = (json \ "_links" \ "next").asOpt[String]

          next match {
            case Some(url) => batcher(url, accumulator, batchResult.toList)
            case None =>
              if(accumulator.nonEmpty) batchResult ++= batchedCont(accumulator)
              Future.successful(batchResult.toList)
          }
        }
      }

      batcher(mid, List(), List())
    }
  }

  def fetch2[B <: Model](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10)
                       (implicit r: Reads[B]): Future[List[B]] = Util(start, mid, end, method, retries, r).fetch

  def fetch2WithCont[B <: Model, A](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10)(cont: B => A)
                               (implicit r: Reads[B]): Future[List[A]] = Util(start, mid, end, method, retries, r).fetchWithCont(cont)

  def fetch2WithBatchedCont[B <: Model, A](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10)(cont: List[B] => List[A])
                                  (implicit r: Reads[B]): Future[List[A]] = Util(start, mid, end, method, retries, r).fetchWithBatchedcont(cont)

  /**
    * Calls fetchPriv directly. Specify the request model like so: fetch[Model](...)
    *
    * See fetchPriv for parameter doc
    *
    * @param start
    * @param mid
    * @param end
    * @param method
    * @param retries
    * @param r
    * @tparam B
    * @return
    */
  def fetch[B <: Model](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10)
                       (implicit r: Reads[B]): Future[List[B]] = fetchPriv[B, B](start, mid, end, method, retries)()



  /**
    * Calls fetchPriv with a continuation. Specify the continuation's input type and you won't have to call
    * fetchCont[InputType, OutputType]
    *
    * Example:
    *
    * fetchCont(...) { part: Participant => ... }
    *
    * See fetchPriv for parameter doc
    *
    * @param start
    * @param mid
    * @param end
    * @param method
    * @param retries
    * @param cont
    * @param r
    * @tparam B
    * @tparam A
    * @return
    */
  def fetchCont[B <: Model, A](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10)(cont: B => A)
                       (implicit r: Reads[B]): Future[List[A]] = fetchPriv[B, A](start, mid, end, method, retries)(cont)

  /**
    * Private method that fetches everything from a Dataservice URL.
    *
    * If no continuations are passed, default to identity[B] and returns a List of Models. In that case, on must call it
    * with fetchPriv[C, C] where C is the Model.
    *
    * If a continuation is passed, it applies the continuation on every Model
    *
    * @param start the start of the URL
    * @param mid the middle of the URL (taken from next on subListuent calls)
    * @param end the end of the URL (limti=100, or file_format, etc)
    * @param method the HTTP method
    * @param retries the number of allowed retries
    * @param cont the continuation
    * @param r the implicit that transforms the JSON into a model
    * @tparam B the type of the model
    * @tparam A the return type of the continuations
    * @return a future of List of A (the cont applied on every result)
    */
  private def fetchPriv[B <: Model, A](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10)
                              (cont: B => A = identity[B] _)
                              (implicit r: Reads[B]): Future[List[A]] = {

    implicit val system: ActorSystem = ActorSystem()
    system.registerOnTermination {
      System.exit(0)
    }

    implicit val materializer: ActorMaterializer = ActorMaterializer()
    // Create the standalone WS client
    // no argument defaults to a AhcWSClientConfig created from
    // "AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)"

    val client = StandaloneAhcWSClient()

    /**
      * Gets everything from the start-mid-end URL, calling cont on it and accumulating the calls' results into
      * resList
      *
      * @param iter    the current middle part of the URL
      * @param resList the result list
      * @return the completed result list
      */
    def getAllFrom(iter: String, resList: List[A]): Future[List[A]] = {
      /**
        * Sends a request
        *
        * @return the response as a String
        */
      def request(tries: Int = 0): Future[JsValue] = {
        if (tries >= retries) throw new IllegalArgumentException
        val temp: Future[StandaloneWSRequest#Response] = client.url(start + iter + (if (resList.isEmpty) "?" else "&") + end).get()
        temp.flatMap { resp =>
          val code = resp.status
          if (code < 200 || code >= 300) request(tries + 1)
          else Future(resp.body[JsValue])
        }
      }

      request().flatMap { json =>
        val results: Seq[JsValue] = json("results").as[Array[JsObject]]

        val models = resList ++ results.map( result => cont(result.as[B]) )


        val next: Option[String] = (json \ "_links" \ "next").asOpt[String]

        next match {
          case Some(url) => getAllFrom(url, models)
          case None => Future.successful(models)
        }
      }
    }

    getAllFrom(mid, List())
  }


}