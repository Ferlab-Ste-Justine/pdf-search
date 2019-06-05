

import org.apache.http.HttpHost
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest
import org.elasticsearch.action.admin.indices.get.GetIndexRequest
import org.elasticsearch.action.bulk.{BulkRequest, BulkResponse}
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.elasticsearch.common.Strings
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.common.xcontent.{XContentBuilder, XContentType}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}


case class IndexingRequest(name: String, text: String, words: Iterable[String], dataType: String = "local", fileFormat: String = "local", kfId: String = "local", index: String = "qsearch")

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
  private val bulkedFutures = ListBuffer[Future[BulkResponse]]()
  private var bulked = new BulkRequest()

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

    /*
    Try to create the index. If it already exists, don't do anything
     */
    try {
      esClient.indices().create(request, RequestOptions.DEFAULT)
    } catch {
      case e: Exception => //e.printStackTrace()
    }
  }

  /**
    * Indexes an IndexingRequest into ES using a Future on which we directly wait (improves performance: while we wait
    * for the response we're blocked, so other threads can move.
    *
    * @param req the request to index
    */
  def index(req: IndexingRequest): Unit = Await.result(Future[Unit](esClient.index(makeIndexRequest(req), RequestOptions.DEFAULT)), Duration.Inf)

  /**
    * Makes an ES IndexRequest from an IndexingRequest
    *
    * @param req
    * @return
    */
  private def makeIndexRequest(req: IndexingRequest) = {
    val request = new IndexRequest(req.index)
    request.`type`("_doc")
    request.source(makeJson(req))

    request
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

  def makeJson(req: IndexingRequest): XContentBuilder = {
    val json = jsonBuilder

    json.startObject()
    json.field("name", req.name)
    json.field("text", req.text)
    json.field("data_type", req.dataType)
    json.field("kf_id", req.kfId)
    json.field("file_format", req.fileFormat)

    json.startArray("words")

    req.words.foreach { word =>
      json.value(word)
    }

    json.endArray()

    json.endObject()

    json
  }

  def bulkIndex(reqs: List[IndexingRequest]): Unit = {
    val bulked = new BulkRequest()

    reqs.foreach(req => bulked.add(makeIndexRequest(req)))

    esClient.bulk(bulked, RequestOptions.DEFAULT)
  }

  def bulkIndexAsync(reqs: List[IndexingRequest]): Future[Unit] = {

    val bulked = new BulkRequest()

    reqs.foreach(req => bulked.add(makeIndexRequest(req)))

    val p = Promise[Unit]()
    val listener = new ActionListener[BulkResponse]() {
      def onResponse(bulkResponse: BulkResponse): Unit = p.success()

      def onFailure(e: Exception): Unit = p.failure(e)
    }

    esClient.bulkAsync(bulked, RequestOptions.DEFAULT, listener)
    p.future
  }

  def bulkIndexAsync2(reqs: List[String]): Future[Unit] = {

    val bulked = new BulkRequest()

    reqs.foreach(req => bulked.add(makeIndexRequest(req)))

    val p = Promise[Unit]()
    val listener = new ActionListener[BulkResponse]() {
      def onResponse(bulkResponse: BulkResponse): Unit = p.success()

      def onFailure(e: Exception): Unit = p.failure(e)
    }

    esClient.bulkAsync(bulked, RequestOptions.DEFAULT, listener)
    p.future
  }

  def bulkIndex(req: IndexingRequest): Unit = {

    val request = makeIndexRequest(req)

    bulked.synchronized {
      bulked.add(request)
      if (bulked.numberOfActions() >= bulking) {
        sendBulk(bulked)
        bulked = new BulkRequest()
      }
    }
  }

  private def sendBulk(bulked: BulkRequest) = {
    bulkedFutures += Future[BulkResponse](esClient.bulk(bulked, RequestOptions.DEFAULT))
  }

  def cleanup(): Unit = {
    if (bulked.numberOfActions() > 0) sendBulk(bulked)

    Await.result(Future.sequence(bulkedFutures), Duration.Inf)
  }
}
