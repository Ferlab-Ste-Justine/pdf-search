import java.io.File

import Main.argMap
import Model.InternalImplicits._
import Model.Utils._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Random

sealed trait Model {

  protected def toIndexingRequest: Future[IndexingRequest2]

  def toJson: Future[String] = toIndexingRequest.map(_.toJson)
}

case class Participant(kf_id: String, ethnicity: Option[String], race: Option[String], gender: Option[String], family: Option[String]) extends Model {
  protected def toIndexingRequest: Future[IndexingRequest2] = {
    val fam = family match {
      case None => "null"
      case Some(value: String) => value.substring(value.lastIndexOf('/') + 1, value.length)
    }

    val asJson: JsValue = Json.toJson(this)(Json.writes[Participant])

    val asMap = asJson.as[Map[String, String]] + ("family" -> fam)

    val text = asMap.map{ case (key, value) =>
      s"${key.capitalize}: $value"
    }.mkString(", ")

    val words: List[String] = asMap.values.toList

    Future.successful(IndexingRequest2(kf_id, text, s"Participant $kf_id", words, "participant", "participant"))
  }
}

case class PDF(kf_id: String, external_id: Option[String], data_type: Option[String], file_name: Option[String], file_format: Option[String]) extends Model {
  protected def toIndexingRequest: Future[IndexingRequest2] = {

    external_id.map{ key =>
      val temp = new File("./input").listFiles

      val stream = temp(Random.nextInt(temp.size))//s3Downloader.download(key)

      Future{
        val text = ocrParser.parsePDF(stream)

        IndexingRequest2(kf_id, text, file_name, nlpParser.getLemmas(text), file_format, data_type)
      }

    }.getOrElse(Future.failed(new Exception("PDF has no external id (S3 key)")))

  }
}

private case class IndexingRequest2(kf_id: String, text: String, name: Option[String], words: Iterable[String], file_format: Option[String], data_type: Option[String]) {
  def toJson: String = Json.toJson(this).toString()
}

object Model {
  object Utils {
    val ocrParser = new OCRParser
    val nlpParser = new NLPParser
    val s3Downloader = new S3Downloader(argMap("bucket"))
  }

  object InternalImplicits {
    implicit def stringToOption(value: String): Option[String] = Option(value)  //syntactic sugar; "string" instead of Some("string")

    implicit val writeIndexingRequest: Writes[IndexingRequest2] = Json.writes[IndexingRequest2]
  }

  object ExternalImplicits {
    implicit val readPDF: Reads[PDF] = Json.reads[PDF]

    implicit val readParticipant: Reads[Participant] = (Json.reads[Participant] and (__ \ "_links" \ "family").readNullable[String]) { (participant, family) =>
      participant.copy(family = family)
    }

  }
}

