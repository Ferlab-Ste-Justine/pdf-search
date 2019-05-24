import java.io.File

import org.scalatest.{FlatSpec, FunSuite, Matchers}

class NLPParserTest extends FlatSpec with Matchers {
    val nlpParser = new NLPParser

    "nlp" should "return correct lemmas with Brain" in {
        val result = nlpParser.getLemmas("\"Brain cancer is bad. This sucks: it is not fun, man...\"\n")
        result shouldBe Array("brain", "cancer", "fun", "man")
    }

    it should "return correct lemmas with Yoda" in {
        val result = nlpParser.getLemmas("\"Omg ... Yoda is so COOL! \n {}}}}{ He's amzing, and I wish the movies showed more of him\"\n")
        result shouldBe Array("omg", "yoda", "movie")
    }
}
