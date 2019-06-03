import org.elasticsearch.common.Strings
import org.scalatest.{FlatSpec, Matchers, PrivateMethodTester}

class ESIndexerTest extends FlatSpec with Matchers with PrivateMethodTester {
    val esIndexer = new ESIndexer
    //val publicMakeJson: PrivateMethod[Array[String]] = PrivateMethod[Array[String]]('makeJson)

    //private tests: https://stackoverflow.com/a/43650990


    "es.makeJson" should """return correct JSON for FileLemmas 1""" in {
        val result = Strings.toString(esIndexer.makeJson(IndexingRequest("allo", "salut", Array("1", "2"))))
        result shouldBe "{\"name\":\"allo\",\"text\":\"salut\",\"data_type\":\"local\",\"kf_id\":\"local\",\"file_format\":\"local\",\"words\":[\"1\",\"2\"]}"
    }

    it should """return correct JSON for FileLemmas 2""" in {
        val result = Strings.toString(esIndexer.makeJson(IndexingRequest("yoda", "manifesto", Array("gazon", "vert"))))
        result shouldBe """{"name":"yoda","text":"manifesto","data_type":"local","kf_id":"local","file_format":"local","words":["gazon","vert"]}"""
    }
}
