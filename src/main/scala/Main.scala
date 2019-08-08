import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import Model.ExternalImplicits._

object Main {

  case class ArgMap(esurl: String = "http://localhost:9200", starturl: String = "https://kf-api-dataservice.kids-first.io", endurl: String = "limit=100&visible=true")
  val argMap: ArgMap = {
    @tailrec
    def mapFromArgsIter(argList: Array[String], argMap: Map[String, String] = Map()): Map[String, String] = argList match {
      case Array() => argMap
      case Array(h, _*) =>
        val Array(k, v) = h.toLowerCase.split("=")

        mapFromArgsIter(argList.tail, argMap + (k -> v))
    }

    import CaseClassUtils._
    import scala.io.BufferedSource
    import java.nio.file.{Files, Paths}

    if(Files.exists(Paths.get("conf.properties"))) {
      val bufferedSource: BufferedSource = scala.io.Source.fromFile("conf.properties")
      val lines = bufferedSource.getLines().toArray
      bufferedSource.close()

      ArgMap().fromMap(mapFromArgsIter(lines))

    } else ArgMap()
  }

  val ESIndexer: ESIndexer = new ESIndexer(argMap.esurl)

  def main(args: Array[String]) {
    val studyList = System.getenv("KF_STUDY_ID").split(" ").toList.map(_.toLowerCase())
    val releaseID = System.getenv("KF_RELEASE_ID").toLowerCase()

    val startTime = System.currentTimeMillis()

    Await.result(Future.sequence(studyList.map(indexStudy(_, releaseID))), Duration.Inf)

    println("took " + (System.currentTimeMillis() - startTime) / 1000 + " seconds")
    System.exit(0)
  }

  def indexStudy(studyID: String, releaseID: String): Future[List[List[Unit]]] = {
    val boundIndexer = ESIndexer.forIndex(s"quicksearch_${studyID}_$releaseID")

    val pt: Future[List[Future[Unit]]] = URLIterator.fetchWithBatchedCont(argMap.starturl, s"/participants?study_id=$studyID", argMap.endurl) { participants: List[Participant] =>
      boundIndexer.bulkIndexAsync(Future.sequence(participants.map(_.toJson)))
    }

    val pdf: Future[List[Future[Unit]]] = URLIterator.fetchWithCont(argMap.starturl, s"/genomic-files?study_id=$studyID", s"file_format=pdf&${argMap.endurl}") { pdf: PDF =>
      //start a future to do: S3 -> OCR -> NLP -> ES
      boundIndexer.indexAsync(pdf.toJson)
    }

    Future.sequence(List(pt, pdf).map(f => f.map(l => Future.sequence(l)).flatten))
  }
}