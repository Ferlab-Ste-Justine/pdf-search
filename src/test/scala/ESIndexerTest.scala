import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}

class ESIndexerTest extends FlatSpec with Matchers with PrivateMethodTester {
    val esIndexer = new ESIndexer
    //val publicMakeJson: PrivateMethod[Array[String]] = PrivateMethod[Array[String]]('makeJson)

    //private tests: https://stackoverflow.com/a/43650990

    "es.makeJson" should """return [{title: allo, text: salut}] with AdminFile(allo, salu)t""" in {
        val result = esIndexer.makeJson(AdminFile("allo", "salut"))
        result shouldBe Array("{\"title\":\"allo\",\"text\":\"salut\"}")
    }

    it should """return [{title: allo, word: le ciel, tag: bleu}] with AdminWord(allo, [(le ciel, bleu)])""" in {
        val result = esIndexer.makeJson(AdminWord("allo", Array(("le ciel", "bleu"))))
        result shouldBe Array("{\"title\":\"allo\",\"word\":\"le ciel\",\"tag\":\"bleu\"}")
    }

    it should """return [{title: allo, word: le ciel, tag: bleu}, {title: allo, word: gazon, tag: vert}] with AdminWord(allo, [(le ciel, bleu), (gazon, vert)])""" in {
        val result = esIndexer.makeJson(AdminWord("allo", Array(("le ciel", "bleu"), ("gazon", "vert"))))
        result shouldBe Array("{\"title\":\"allo\",\"word\":\"le ciel\",\"tag\":\"bleu\"}", "{\"title\":\"allo\",\"word\":\"gazon\",\"tag\":\"vert\"}")
    }

    /* TODO TEMP refactoring adminFileWordKeyword...
    "es.makeJson" should """return [{title: allo, text: salut, words[{word: le ciel, tag: bleu}, {word: gazon, tag: vert}]] with AdminWord(allo, [(le ciel, bleu), (gazon, vert)])""" in {
        val result = esIndexer invokePrivate publicMakeJson(AdminFileWordKeyword("allo", "salut", Array(("le ciel", "bleu"), ("gazon", "vert"))))
        result shouldBe Array("{\"title\":\"allo\",\"text\":\"salut\",\"words\":[{\"word\":\"le ciel\",\"tag\":\"bleu\"},{\"word\":\"gazon\",\"tag\":\"vert\"}]}")
    }*/
}
