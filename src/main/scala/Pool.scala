import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, _}
import scala.concurrent.duration.Duration

/**
  * Logical separation: all generic pooling functions go here
  */
object Pool {

    /**
      * Applies f on every element of input "collection", then transforms the list of Futures obtained from f into one
      * single future.
      *
      * Then, waits for the single Future to finish and returns the value of the future (a ListBuffer[B])
      *
      * Accepts a Traversable as parameter because Traversable is the highest in the Collection Hierarchy (foreach is
      * needed).
      *
      * @param collection the collection to apply f on
      * @param f the function to apply (must return a Future)
      * @tparam A type of input
      * @tparam B type of output
      * @return the output ListBuffer
      */
    def distribute[A, B](collection: Traversable[A], f: A => Future[B]): ListBuffer[B] = {

        /*
        Future.sequence requires a List.

        We could map on collection and then use toList to get it, but that's O(2n).

        Appending a mutable ListBuffer and then directly calling Future.sequence on it is O(n)
         */
        val listBuffer = new ListBuffer[Future[B]]

        collection.foreach(x => listBuffer.append(f(x)) )

        Await.result(Future.sequence(listBuffer), Duration.Inf)
    }
}
