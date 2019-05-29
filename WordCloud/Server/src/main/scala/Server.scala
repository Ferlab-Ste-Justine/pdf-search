import akka.actor.ActorSystem
import akka.http.javadsl.server.RouteResult
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

object Server extends App {

    val host = "0.0.0.0"
    val port = 9000

    val es = new ESIndexer()

    implicit val system: ActorSystem = ActorSystem(name = "todoapi")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    import system.dispatcher

    def functionnalSplit(str: String, splitter: String = "&"): List[String] = {

        @tailrec
        def iter(string: String, list: List[String]): List[String] = {
            val index = string.indexOf(splitter)

            if (index == -1) list :+ string
            else iter(string.substring(index + 1, string.length), list :+ string.substring(0, index))
        }

        iter(str, List())
    }

    import akka.http.scaladsl.server.Directives._

    def route = cors() {
        pathPrefix("words") {
            pathEndOrSingleSlash {
                complete(es.search())
            } ~
            path(""".+""".r) { str =>

                get {
                    complete(es.search(functionnalSplit(str)))
                }
            }
        }
    }

    val binding = Http().bindAndHandle(route, host, port)
    binding.onComplete {
        case Success(_) => println("Success!")
        case Failure(error) => println(s"Failed: ${error.getMessage}")
    }

    import scala.concurrent.duration._
    Await.result(binding, 3.seconds)
}