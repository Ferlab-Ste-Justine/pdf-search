import Model.ExternalImplicits._
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.Json

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ModelTest extends FlatSpec with Matchers {
  var participant: Participant = Json.obj("kf_id" -> "Yoda", "ethnicity" -> "Light side of the Force", "race" -> "Jedi Master", "_links" -> "nothing").as[Participant]

  "participant reading" should "be accurate and handle Nones" in {
    participant shouldBe Participant("Yoda", Some("Light side of the Force"), Some("Jedi Master"), None, None)
  }

  "IndexingRequest writing" should "be accurate and handle Nones from Participant" in {
    Await.result(participant.toJson, Duration.Inf) shouldBe Json.obj(
      "kf_id" -> "Yoda", "text" -> "Kf_id: Yoda, Ethnicity: Light side of the Force, Race: Jedi Master",
      "name" -> "Participant Yoda", "words" -> Json.arr("Yoda", "Light side of the Force", "Jedi Master"),
      "file_format" -> "participant", "data_type" -> "participant"
    ).toString()
  }

  it should "be accurate and handle Nones" in {
    val req = IndexingRequest("Yoda", "Yoda is the most powerful being in the $1/1414i1091379)!#*($&! Galaxy{}", None, Some("true"), Some("false"), Some("null"))

    req.toJson shouldBe Json.obj(
      "kf_id" -> "Yoda", "text" -> "Yoda is the most powerful being in the $1/1414i1091379)!#*($&! Galaxy{}",
      "words" -> Json.arr("true"), "file_format" -> "false", "data_type" -> "null"
    ).toString()
  }
}




