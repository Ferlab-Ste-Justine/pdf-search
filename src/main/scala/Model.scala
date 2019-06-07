import java.io.File

import Main.argMap
import Model.InternalImplicits._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Random

trait Model {

  protected def toIndexingRequest: Future[IndexingRequest]

  def toJson: Future[String] = toIndexingRequest.map(_.toJson)
}

case class Participant(kf_id: String, ethnicity: Option[String], race: Option[String], gender: Option[String], family: Option[String]) extends Model {
  protected def toIndexingRequest: Future[IndexingRequest] = {
    val fam = family match {
      case None => "null"
      case Some(value: String) => value.substring(value.lastIndexOf('/') + 1, value.length)
    }

    val asJson: JsValue = Json.toJson(this)(Json.writes[Participant])

    val asMapInit = asJson.as[Map[String, String]]

    val asMap = family match {
      case None => asMapInit
      case Some(value: String) => asMapInit + ("family" -> value.substring(value.lastIndexOf('/') + 1, value.length))
    }

    val text = asMap.map{ case (key, value) =>
      s"${key.capitalize}: $value"
    }.mkString(", ")

    val words: List[String] = asMap.values.toList

    Future.successful(IndexingRequest(kf_id, text, Some(s"Participant $kf_id"), words, Some("participant"), Some("participant")))
  }
}

case class PDF(kf_id: String, external_id: Option[String], data_type: Option[String], file_name: Option[String], file_format: Option[String]) extends Model {
  import Model.Internals._

  protected def toIndexingRequest: Future[IndexingRequest] = {

    external_id.map{ key =>
      val temp = new File("./input").listFiles

      val stream = temp(Random.nextInt(temp.size))//s3Downloader.download(key)

      Future{
        val text = ocrParser.parsePDF(stream)

        IndexingRequest(kf_id, text, file_name, nlpParser.getLemmas(text), file_format, data_type)
      }

    }.getOrElse(Future.failed(new Exception("PDF has no external id (S3 key)")))

  }
}

case class Holder(valList: List[Option[String]]) extends Model {
  override protected def toIndexingRequest: Future[IndexingRequest] = {
    val temp = valList.foldLeft(List[String]()){ (acc, value) =>
      value.map(acc :+ _).getOrElse(acc)
    }

    Future.successful(IndexingRequest("id", valList.mkString(", "), Some("name"), temp, Some("format"), Some("type")))
  }
}

case class IndexingRequest(kf_id: String, text: String, name: Option[String], words: Iterable[String], file_format: Option[String], data_type: Option[String]) {
  def toJson: String = Json.toJson(this).toString()
}

object Model {

  /*
  Implicits used internally for Models and classes used to parse Models
   */
  object Internals {
    val ocrParser = new OCRParser
    val nlpParser = new NLPParser
    val s3Downloader = new S3Downloader(argMap("bucket"))
  }

  object InternalImplicits {

    implicit val writeIndexingRequest: Writes[IndexingRequest] = Json.writes[IndexingRequest]
  }

  /*
  Implicits needed to create models
   */
  object ExternalImplicits {

    //https://stackoverflow.com/questions/34085663/scala-play-json-format-for-maplocale-string
    def readHolder(list: List[String]): Reads[Holder] = (json: JsValue) => {
      val readMap = json.as[Map[String, String]]

      val listOfFieldValues: List[Option[String]] = list.map( field => if(readMap.isDefinedAt(field)) Some(readMap(field)) else None )

      JsSuccess(Holder(listOfFieldValues))
    }

    implicit val readPDF: Reads[PDF] = Json.reads[PDF]

    implicit val readParticipant: Reads[Participant] = (Json.reads[Participant] and (__ \ "_links" \ "family").readNullable[String]) { (participant, family) =>
      participant.copy(family = family)
    }

  }
}

