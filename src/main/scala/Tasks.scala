import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

/**
  * Transforms a call-by-name into a no-parameter function call to be able to use it in a varargs
  *
  * https://stackoverflow.com/questions/13307418/scala-variable-argument-list-with-call-by-name-possible
  *
  * @param a the call-by-name parameter
  * @tparam A its return type
  * @return the call by name parameter as a function
  */
implicit def bynameToNoarg[A](a: => A): () => A = () => a

class Matcher[T]
object Matcher {
    implicit object ListOfFutureWitness extends Matcher[List[Future[Any]]]
    implicit object ListWitness extends Matcher[List[Any]]
    implicit object FutureOfListWitness extends Matcher[Future[List[Any]]]
    implicit object AnyWitness extends Matcher[Any]
}

/**
  * Syntactic sugar to parallelize the execution of multiple function calls.
  *
  * Creates a Future per block of code to execute and then blocks until all the tasks are done.
  *
  * https://alvinalexander.com/scala/fp-book/how-to-use-by-name-parameters-scala-functions
  *
  * @param codes the function calls to parallelize
  */
case class Tasks[B: Matcher](codes: (() => B)*) {
    Await.result(
        Future.traverse(codes) { code =>
            Future[Unit] {
                val result = code()

                result match {
                    case a: List[Future[Any]] =>
                        Await.result(Future.sequence(a), Duration.Inf)
                    case _: List[Any] =>
                    case a: Future[List[Any]] =>
                        Await.result(a, Duration.Inf)
                }
            }
        }
        , Duration.Inf)
}