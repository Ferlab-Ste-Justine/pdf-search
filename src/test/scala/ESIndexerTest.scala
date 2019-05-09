import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}

class ESIndexerTest extends FlatSpec with Matchers with PrivateMethodTester {
    val esIndexer = new ESIndexer
    val publicMakeJson: PrivateMethod[List[String]] = PrivateMethod[List[String]]('makeJson)

    //private tests: https://stackoverflow.com/a/43650990

    "es.makeJson" should """return [{title: allo, text: salut}] with AdminFile(allo, salu)t""" in {
        val result = esIndexer invokePrivate publicMakeJson(AdminFile("allo", "salut"))
        result shouldBe List("{\"title\":\"allo\",\"text\":\"salut\"}")
    }

    "es.makeJson" should """return [{title: allo, word: le ciel, tag: bleu}] with AdminWord(allo, [(le ciel, bleu)])""" in {
        val result = esIndexer invokePrivate publicMakeJson(AdminWord("allo", Array(("le ciel", "bleu"))))
        result shouldBe List("{\"title\":\"allo\",\"word\":\"le ciel\",\"tag\":\"bleu\"}")
    }

    "es.makeJson" should """return [{title: allo, word: le ciel, tag: bleu}, {title: allo, word: gazon, tag: vert}] with AdminWord(allo, [(le ciel, bleu), (gazon, vert)])""" in {
        val result = esIndexer invokePrivate publicMakeJson(AdminWord("allo", Array(("le ciel", "bleu"), ("gazon", "vert"))))
        result shouldBe List("{\"title\":\"allo\",\"word\":\"le ciel\",\"tag\":\"bleu\"}", "{\"title\":\"allo\",\"word\":\"gazon\",\"tag\":\"vert\"}")
    }

    "es.makeJson" should """return [{title: allo, text: salut, words[{word: le ciel, tag: bleu}, {word: gazon, tag: vert}]] with AdminWord(allo, [(le ciel, bleu), (gazon, vert)])""" in {
        val result = esIndexer invokePrivate publicMakeJson(AdminFileWord("allo", "salut", Array(("le ciel", "bleu"), ("gazon", "vert"))))
        result shouldBe List("{\"title\":\"allo\",\"text\":\"salut\",\"words\":[{\"word\":\"le ciel\",\"tag\":\"bleu\"},{\"word\":\"gazon\",\"tag\":\"vert\"}]}")
    }
}
