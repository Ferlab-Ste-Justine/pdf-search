import Model.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

import scala.concurrent.Future


import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, blocking}

sealed trait Model {
  def toIndexingRequest: Any
}

case class Participant(kf_id: String, ethnicity: Option[String], race: Option[String], gender: Option[String], family: Option[String]) extends Model {
  def toIndexingRequest: IndexingRequest2 = {
    val family = family match {
      case None => "null"
      case Some(value: String) => value.substring(value.lastIndexOf('/') + 1, value.length)
    }

    val asJson: JsValue = Json.toJson(this)(Json.writes[Participant])

    val asMap = asJson.as[Map[String, String]] + ("family" -> family)

    val text = asMap.map{ case (key, value) =>
      s"${key.capitalize}: $value"
    }.mkString(", ")

    val words: Iterable[String] = asMap.values.asInstanceOf[List[String]]

    IndexingRequest2(kf_id, text, s"Participant $kf_id", words, "participant", "participant")
  }

  def toJson: String = Json.toJson(toIndexingRequest).toString()
}

case class PDF(kf_id: String, external_id: Option[String], data_type: Option[String], file_name: Option[String]) extends Model {
  def toIndexingRequest: Future[IndexingRequest2] = Future{Json.toJson(this).toString()}
}

case class IndexingRequest2(kf_id: String, text: String, name: String, words: Iterable[String], file_format: String, data_type: String) {
  def toJson: String = Json.toJson(this).toString()
}

object Model {
  object Implicits {

    implicit val writeIndexingRequest: Writes[IndexingRequest2] = Json.writes[IndexingRequest2]

    implicit val readPDF: Reads[PDF] = Json.reads[PDF]

    implicit val writePDF: Writes[PDF] = (pdf: PDF, thing: String) => {
      Json.obj(
        "a" -> "b"
      )
    }

    implicit val readParticipant: Reads[Participant] = (Json.reads[Participant] and (__ \ "_links" \ "family").readNullable[String]) { (participant, family) =>
      participant.copy(family = family)
    }

    implicit val writeParticipant: Writes[Participant] = (participant: Participant) => {
      val family = participant.family match {
        case None => "null"
        case Some(value) => value.substring(value.lastIndexOf('/') + 1, value.length)
      }

      val asJson: JsValue = Json.toJson(participant)(Json.writes[Participant])

      val asMap = asJson.as[Map[String, String]] + ("family" -> family)

      val text = asMap.map{ case (key, value) =>
        s"${key.capitalize}: $value"
      }.mkString(", ")

      val words = asMap.values

      Json.obj(
        "kf_id" -> participant.kf_id,
        "text" -> text,
        "name" -> s"Participant ${participant.kf_id}",
        "words" -> words,
        "file_format" -> "participant",
        "data_type" -> "participant"
      )
    }


  }
}

