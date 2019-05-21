import java.io.File

import org.scalatest.{FlatSpec, FunSuite, Matchers}

class NLPParserTest extends FlatSpec with Matchers {
    val nlpParser = new NLPParser()

    "nlp" should "tag everything correctly with input \"Brain cancer is bad. This sucks: it is not fun, man...\"\n when asking for tokentags" in {
        val result = nlpParser.getTokenTags("\"Brain cancer is bad. This sucks: it is not fun, man...\"\n")
        result shouldBe Array(("\"","``"), ("Brain","NN"), ("cancer","NN"), ("is","VBZ"), ("bad","JJ"), (".","."), ("This","DT"), ("sucks","VBZ"), (":",":"), ("it","PRP"), ("is","VBZ"), ("not","RB"), ("fun","NN"), (",",","), ("man","NN"), ("","FW"), (".","."), ("\"","''"))
    }

    it should "return [Brain cancer sucks man] with input \"Brain cancer is bad. This sucks: it is not fun, man...\"\n when asking for nouns" in {
        val result = nlpParser.getTokenTags("\"Brain cancer is bad. This sucks: it is not fun, man...\"\n", true)
        result shouldBe Array(("brain","NN"), ("cancer","NN"), ("fun","NN"), ("man","NN"), ("","FW"))
    }
}
