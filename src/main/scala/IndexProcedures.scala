import java.io.File

import Main.argMap
import Model.ExternalImplicits._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object IndexProcedures {
  val ocrParser = new OCRParser
  val nlpParser = new NLPParser
  //TODO when we stop testing locally, remove these first two vals and remove indexPDFLocal
  val esIndexer = new ESIndexer(argMap("esurl"))

  def indexParticipants(start: String, mid: String, end: String): Future[List[Unit]] = {
    URLIterator.fetchWithBatchedCont(start, mid, end) { participants: List[Participant] =>
      esIndexer.bulkIndexAsync(Future.sequence(participants.map(_.toJson)))
      println("Participant.........................")
      ()
    }
  }

  def indexPDFRemote(start: String, mid: String, end: String = ""): Future[List[Unit]] = {
    URLIterator.fetchWithCont(start, mid, s"file_format=pdf&$end") { pdf: PDF => //TODO change study ID to not-harcoded once integrated into ETL
      //start a future to do: S3 -> OCR -> NLP -> ES
      esIndexer.indexAsync(pdf.toJson)
      ()
    }
  }

  /**
    * Indexes files from a local directory into ES
    *
    * @param path the path to the folder containing the files
    */
  def indexPDFLocal(path: String): Future[List[Unit]] = {
    Future.traverse(new File(path).listFiles().toList) { file: File =>
      //start a future to do: OCR -> NLP -> ES

      Future {
        val text = ocrParser.parsePDF(file)

        val lemmas = nlpParser.getLemmas(text)

        println("PDF.........................")

        esIndexer.indexAsync(IndexingRequest("local", text, Some(file.getName), lemmas, Some("pdf"), Some("pdf")).toJson)


      }.flatten

    }
  }
}
