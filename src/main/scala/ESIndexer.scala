import org.apache.http.HttpHost
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.elasticsearch.common.Strings
import org.elasticsearch.common.xcontent.{XContentBuilder, XContentType}
import org.elasticsearch.common.xcontent.XContentFactory._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


sealed trait IndexingRequest { //represents an IndexRequest into index of name getClass
    override def toString: String = this.getClass.getName
    def index: String
    def title: String
}

case class FileLemmas(title: String, text: String, words: Iterable[String], typ: String = "local", index: String = "filelemmas") extends IndexingRequest

class ESIndexer(url: String = "http://localhost:9200") {
    //https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-docs-index.html
    //https://www.elastic.co/guide/en/elasticsearch/reference/7.0/docs-index_.html
    //https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
    //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-document-bulk.html

    val esClient = new RestHighLevelClient(RestClient.builder(HttpHost.create(url)))

    //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-create-index.html
    //https://discuss.elastic.co/t/elasticsearch-total-term-frequency-and-doc-count-from-given-set-of-documents/115223
    //https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-termvectors.html

    def initIndexes(): Unit = {
        val jsonAdminFileLemma = jsonBuilder

        jsonAdminFileLemma.startObject()
            jsonAdminFileLemma.startObject("_doc")
                jsonAdminFileLemma.startObject("properties")
                    jsonAdminFileLemma.startObject("title")
                    jsonAdminFileLemma.field("type", "text")
                    jsonAdminFileLemma.field("analyzer", "english")  //use the custom analyser we're creating in jsonSettings
                    jsonAdminFileLemma.endObject()

                    jsonAdminFileLemma.startObject("text")
                    jsonAdminFileLemma.field("type", "text")
                    jsonAdminFileLemma.field("analyzer", "english")  //use the custom analyser we're creating in jsonSettings
                    jsonAdminFileLemma.endObject()

                    jsonAdminFileLemma.startObject("words")
                    jsonAdminFileLemma.field("type", "keyword")
                    jsonAdminFileLemma.endObject()

                    jsonAdminFileLemma.startObject("data_type")
                    jsonAdminFileLemma.field("type", "keyword")
                    jsonAdminFileLemma.endObject()
                jsonAdminFileLemma.endObject()
            jsonAdminFileLemma.endObject()
        jsonAdminFileLemma.endObject()

        val request = new CreateIndexRequest("filelemmas")
        request.mapping("_doc",
            Strings.toString(jsonAdminFileLemma),
            XContentType.JSON);

        /*
        Try to create the index. If it already exists, don't do anything
         */
        try {
            esClient.indices().create(request, RequestOptions.DEFAULT)
        } catch {
            case e: Exception => //e.printStackTrace()
        }
    }

    private def makeIndexRequest(req: FileLemmas) = {
        val request = new IndexRequest(req.index)
        request.`type`("_doc")
        request.source(makeJson(req))

        request
    }
    def index(req: FileLemmas): Future[Unit] = Future[Unit] (esClient.index(makeIndexRequest(req), RequestOptions.DEFAULT))

    def makeJson(req: IndexingRequest): XContentBuilder = req match {
        case req: FileLemmas =>
            val json = jsonBuilder

            json.startObject()
            json.field("title", req.title)
            json.field("text", req.text)
            json.field("type", req.typ)

            json.startArray("words")

            req.words.foreach{ word =>
                json.value(word)
            }

            json.endArray()

            json.endObject()

            json
    }
}
