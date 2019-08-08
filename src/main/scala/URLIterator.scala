import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.json.{JsObject, JsValue, Reads}
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.StandaloneWSRequest
import play.api.libs.ws.ahc.StandaloneAhcWSClient

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//https://github.com/netty/netty/issues/7768

object URLIterator {

  def fetchGeneric(start: String, mid: String, end: String = "", fields: List[String] = List(), method: String = "GET", retries: Int = 10):
  Future[List[Holder]] = Util(start, mid, end, method, retries)(Model.ExternalImplicits.readHolder(fields)).fetch

  def fetch[B <: Model](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10)
                       (implicit r: Reads[B]): Future[List[B]] = Util(start, mid, end, method, retries).fetch

  def fetchWithCont[B <: Model, A](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10)(cont: B => A)
                                  (implicit r: Reads[B]): Future[List[A]] = Util(start, mid, end, method, retries).fetchWithCont(cont)

  def fetchWithBatchedCont[B <: Model, A](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10, batchSize: Int = 1500)(cont: List[B] => A)
                                         (implicit r: Reads[B]): Future[List[A]] = Util(start, mid, end, method, retries).fetchWithBatchedcont(cont, batchSize)

  private sealed case class Util[B <: Model](start: String, mid: String, end: String = "", method: String = "GET", retries: Int = 10)(implicit reader: Reads[B]) {
    implicit val system: ActorSystem = ActorSystem()
    system.registerOnTermination {
      System.exit(0)
    }

    implicit val materializer: ActorMaterializer = ActorMaterializer()
    // Create the standalone WS client
    // no argument defaults to a AhcWSClientConfig created from
    // "AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)"

    val client = StandaloneAhcWSClient()

    def fetch: Future[List[B]] = fetchWithCont(identity)

    def fetchWithCont[A](cont: B => A): Future[List[A]] = { //wrapper allors us to not pass cont
      def singler(iter: String, resList: List[A]): Future[List[A]] = {
        request(iter).flatMap { json =>
          val results: Seq[JsValue] = json("results").as[Array[JsObject]]

          val models = resList ++ results.map(result => cont(result.as[B]))

          val next: Option[String] = (json \ "_links" \ "next").asOpt[String]

          next match {
            case Some(url) => singler(url, models)
            case None => Future.successful(models)
          }
        }
      }

      singler(mid, List())
    }

    def fetchWithBatchedcont[A](batchedCont: List[B] => A, batchSize: Int = 1500): Future[List[A]] = {
      def batcher(iter: String, builder: List[B], resList: List[A]): Future[List[A]] = {
        request(iter).flatMap { json =>
          val results: Seq[JsValue] = json("results").as[Array[JsObject]]

          val batchResult = ListBuffer[A]() ++= resList

          val accumulator = results.foldLeft(builder) { (acc: List[B], result) =>
            val asModel = result.as[B]

            if (acc.length >= batchSize) {
              batchResult += batchedCont(acc)
              List(asModel)
            } else {
              acc :+ asModel
            }

          }

          val next: Option[String] = (json \ "_links" \ "next").asOpt[String]

          next match {
            case Some(url) => batcher(url, accumulator, batchResult.toList)
            case None =>
              if (accumulator.nonEmpty) batchResult += batchedCont(accumulator)
              Future.successful(batchResult.toList)
          }
        }
      }

      batcher(mid, List(), List())
    }

    /**
      * Sends a request
      *
      * @return the response as a String
      */
    def request(iter: String, tries: Int = 0): Future[JsValue] = {
      def requestIter(tries: Int): Future[JsValue] = {
        if (tries >= retries) throw new IllegalArgumentException

        val temp: Future[StandaloneWSRequest#Response] = client.url(start + iter + (if (!iter.contains("?")) "?" else "&") + end).get() //test if iter contains ?: when asking for study id next will contain it
        temp.flatMap { resp =>
          val code = resp.status
          if (code < 200 || code >= 300) requestIter(tries + 1)
          else Future(resp.body[JsValue])
        }
      }

      requestIter(0)
    }
  }
}