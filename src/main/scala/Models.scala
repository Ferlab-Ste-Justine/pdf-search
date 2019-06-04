/*
import play.api.libs.json.Json

case class Participant(kf_id: String, ethnicity: String, race: String, gender: String)

object Models {

  object Implicits {

    implicit val readParticipant = Json.reads[Participant]
  }

}
*/


import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{__, Json, Reads}


case class Participant(kf_id: String, ethnicity: Option[String], race: Option[String], gender: Option[String], family: Option[String])

object Models {
  object Implicits {

    implicit val readParticipant: Reads[Participant] = (Json.reads[Participant] and (__ \ "_links" \ "family").readNullable[String]) { (participant, family) =>
      participant.copy(family = family)
    }
  }
}

