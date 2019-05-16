import java.io.File

import org.scalatest.{FlatSpec, FunSuite, Matchers}

class NLPParserTest extends FlatSpec with Matchers {
    val nlpParser = new NLPParser()

    "nlp" should "return [Brain cancer sucks man] with input \"Brain cancer is bad. This sucks: it is not fun, man...\"\n" in {
        val result = nlpParser.getNouns("\"Brain cancer is bad. This sucks: it is not fun, man...\"\n")
        result shouldBe Array("Brain", "cancer", "fun", "man", "")
    }
}
