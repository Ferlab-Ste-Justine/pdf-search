import Model.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

sealed trait Model {
  def toJson: String
}

case class Participant(kf_id: String, ethnicity: Option[String], race: Option[String], gender: Option[String], family: Option[String]) extends Model {
  def toJson: String = Json.toJson(this).toString()
}

object Model {
  object Implicits {

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

