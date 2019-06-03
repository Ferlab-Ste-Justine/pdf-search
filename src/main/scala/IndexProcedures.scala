import java.io.{File, FileInputStream, InputStream}

import Main.{esIndexer, nlpParser, ocrParser, s3Downloader}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object IndexProcedures {

  def indexParticipants(start: String, mid: String, end: String): Future[List[Unit]] = {
    Future.sequence(
      URLIterator.batchedApplyOnAllFrom(start, mid, end, List("kf_id", "ethnicity", "race", "gender"), List("family"), batchSize = 1500) { participants: List[Map[String, String]] =>
        val requests = participants.map { participant: Map[String, String] =>

          val familyLink = participant("_links.family")
          val family = familyLink.substring(familyLink.lastIndexOf('/') + 1, familyLink.length)

          val text = s"KF_ID: ${participant("kf_id")}. Ethnicity: ${participant("ethnicity")}. Race: ${participant("race")}. Gender: ${participant("gender")}. Family_id: $family."
          val words = participant.values.slice(1, 3)

          IndexingRequest("Participant " + participant("kf_id"), text, words, "participant", "participant", participant("kf_id"))

        }
        esIndexer.bulkIndexAsync(requests)
      }
    )
  }

  /**
    * Indexes PDFs into ES.
    *
    * @param pdf        the PDF as an InputStream. Uses call-by-name to create a future on the download of said stream
    * @param name       the name of the PDF
    * @param dataType   the datatype of the PDF
    * @param fileFormat the format of the PDF (always PDF, but we're not hard-coding it...)
    * @param kfId       the Kid's First ID of the PDF
    * @return String representing wether or not the indexing succeeded
    */
  private def indexPDF(pdf: => InputStream, name: String, dataType: String = "local", fileFormat: String = "pdf", kfId: String = "local"): Future[String] = Future[String] { //do: OCR -> NLP -> ES
    try {
      val text = ocrParser.parsePDF(pdf)

      esIndexer.index(IndexingRequest(name, text, nlpParser.getLemmas(text), dataType, fileFormat, kfId))

      s"$name"

    } catch {
      case e: java.net.ConnectException =>
        e.printStackTrace()
        println("ES or connection error; exiting now...")
        System.exit(1)
        ""
      case _: Exception => s"Failed indexing $name"
    }
  }

  /**
    * Prints things in the list of pdf indexing result. Returns
    *
    * @param list
    * @return
    */
  private def printReport(list: List[String]): List[String] = {
    def printtab(str: String): Unit = println("\t" + str)

    @tailrec
    def printIter(main: List[String], failures: List[String]): Unit = main match {
      case x :: tail =>
        if (x.startsWith("Failed")) printIter(tail, failures :+ x)
        else {
          printtab(x)
          printIter(tail, failures)
        }
      case List() =>
        println("Failures:")
        failures.foreach(printtab)
    }

    println("Successes:")
    printIter(list, List())

    list
  }

  def indexPDFRemote(start: String, mid: String, end: String): Future[List[String]] = {
    Future.sequence{
      URLIterator.applyOnAllFrom(start, mid, end, List("external_id", "data_type", "file_format", "file_name", "kf_id")) { edffk =>
        //start a future to do: S3 -> OCR -> NLP -> ES
        indexPDF(s3Downloader.download(edffk("external_id")), edffk("file_name"), edffk("data_type"), edffk("file_format"), edffk("kf-id"))
      }
    }.map(printReport)
  }

  /**
    * Indexes files from a local directory into ES
    *
    * @param path the path to the folder containing the files
    */
  def indexPDFLocal(path: String): Future[List[String]] = {
    Future.traverse(new File(path).listFiles().toList) { file: File =>
      //start a future to do: OCR -> NLP -> ES
      indexPDF(new FileInputStream(file), file.getName)
    }.map(printReport)
  }
}
