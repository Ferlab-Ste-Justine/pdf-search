

import org.apache.http.HttpHost
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.action.bulk.{BulkRequest, BulkResponse}
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.action.{ActionListener, ActionResponse}
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.elasticsearch.common.Strings
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.common.xcontent.XContentType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class ESIndexer(url: String = "http://localhost:9200", bulking: Int = 1500) {
  //https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-docs-index.html
  //https://www.elastic.co/guide/en/elasticsearch/reference/7.0/docs-index_.html
  //https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
  //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-document-bulk.html

  val esClient = new RestHighLevelClient(RestClient.builder(HttpHost.create(url)))


  //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-create-index.html
  //https://discuss.elastic.co/t/elasticsearch-total-term-frequency-and-doc-count-from-given-set-of-documents/115223
  //https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-termvectors.html

  initIndexes()

  def initIndexes(): Unit = {

    val exists = new GetIndexRequest()
    exists.indices("qsearch")

    if (esClient.indices().exists(exists, RequestOptions.DEFAULT)) return //if index exists, stop

    val jsonAdminFileLemma = jsonBuilder

    jsonAdminFileLemma.startObject()
    jsonAdminFileLemma.startObject("_doc")

      jsonAdminFileLemma.startObject("properties")

        jsonAdminFileLemma.startObject("name")
        jsonAdminFileLemma.field("type", "text")
        jsonAdminFileLemma.field("analyzer", "english") //use the custom analyser we're creating in jsonSettings
        jsonAdminFileLemma.endObject()

        jsonAdminFileLemma.startObject("text")
        jsonAdminFileLemma.field("type", "text")
        jsonAdminFileLemma.field("analyzer", "english") //use the custom analyser we're creating in jsonSettings
        jsonAdminFileLemma.endObject()

        jsonAdminFileLemma.startObject("words")
        jsonAdminFileLemma.field("type", "keyword")
        jsonAdminFileLemma.endObject()

        jsonAdminFileLemma.startObject("file_format")
        jsonAdminFileLemma.field("type", "keyword")
        jsonAdminFileLemma.endObject()

        jsonAdminFileLemma.startObject("data_type")
        jsonAdminFileLemma.field("type", "keyword")
        jsonAdminFileLemma.endObject()

        jsonAdminFileLemma.startObject("kf_id")
        jsonAdminFileLemma.field("type", "keyword")
        jsonAdminFileLemma.endObject()

      jsonAdminFileLemma.endObject()

    jsonAdminFileLemma.endObject()
    jsonAdminFileLemma.endObject()

    val request = new CreateIndexRequest("qsearch")
    request.mapping("_doc",
      Strings.toString(jsonAdminFileLemma),
      XContentType.JSON)

    request.settings(Settings.builder().put("induex.number_of_shards", 1))

    /*
    Try to create the index. If it already exists, don't do anything
     */
    try {
      esClient.indices().create(request, RequestOptions.DEFAULT)
    } catch {
      case e: Exception => //e.printStackTrace()
    }
  }

  def indexAsync(req: Future[String]): Future[Unit] = req.flatMap(indexAsync)

  def indexAsync(req: String): Future[Unit] = {
    val p = Promise[Unit]()
    esClient.indexAsync(makeIndexRequest(req), RequestOptions.DEFAULT, makeListener[IndexResponse](p))
    p.future
  }

  /**
    * Makes an ES IndexRequest from an IndexingRequest
    *
    * @param req
    * @return
    */
  private def makeIndexRequest(req: String) = {
    val request = new IndexRequest("qsearch")
    request.`type`("_doc")
    request.source(req, XContentType.JSON)

    request
  }

  private def makeListener[A <: ActionResponse](p: Promise[Unit]): ActionListener[A] =
    new ActionListener[A]() {
      def onResponse(resp: A): Unit = p.success(resp)

      def onFailure(e: Exception): Unit = p.failure(e)
    }

  def bulkIndexAsync(reqs: Future[List[String]]): Future[Unit] = reqs.flatMap(bulkIndexAsync)

  def bulkIndexAsync(reqs: List[String]): Future[Unit] = {

    val bulked = new BulkRequest()

    reqs.foreach(req => bulked.add(makeIndexRequest(req)))

    val p = Promise[Unit]()
    esClient.bulkAsync(bulked, RequestOptions.DEFAULT, makeListener[BulkResponse](p))
    p.future
  }
}
