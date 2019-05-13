import org.apache.http.HttpHost
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.elasticsearch.common.Strings
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.common.xcontent.XContentType

sealed trait IndexInfoRequest { //represents an IndexRequest into index of name getClass
    override def toString: String = this.getClass.getName
    def index: String
}

//TODO refactor trait into abstract class to use inIndex as super?
//title-text
case class AdminFile(inIndex: String = "adminfile")(title: String, text: String) extends IndexInfoRequest {
    override val index = inIndex
}
//title-word-tag
case class AdminWord(title: String, wordTags: Array[(String, String)]) extends IndexInfoRequest {
    override val index: String = "adminword"
}
//title-text-words[word-tag]
case class AdminFileWord(title: String, text: String, wordTag: Array[(String, String)]) extends IndexInfoRequest {
    override val index: String = "adminfileword"
}

class ESIndexer(url: String = "http://localhost:9200") {
    //https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-docs-index.html
    //https://www.elastic.co/guide/en/elasticsearch/reference/7.0/docs-index_.html
    //https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
    //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-document-bulk.html

    val esClient = new RestHighLevelClient(RestClient.builder(HttpHost.create(url)))

    /**
      * Makes an ES IndexRequest that puts json into index
      *
      * @param index the target ES index
      * @param json the input for index
      * @return the IndexRequest
      */
    private def makeIndexRequest(index: String, json: String): IndexRequest = {
        val request = new IndexRequest(index)
        request.source(json, XContentType.JSON)
    }

    /**
      * Makes a bulk request corresponding to a list of IndexInfoRequest
      *
      * @param requests the IndexInfoRequests
      */
    def bulkIndex(requests: List[IndexInfoRequest]): Unit = {

        val request = new BulkRequest()                             //creates a bulkRequest
        requests.foreach{ req =>                                    //for every IndexInfoRequest
            makeJson(req).foreach{ json =>                          //for every json from said request
                request.add(makeIndexRequest(req.index, json))   //add the the IndexRequest to the bulk request
            }
        }

        /*
        We could use bulkAsync; however, bulk allows us to work with Futures easier AND we're already going Async with
        said futures.

        Bulk will block the ones that get here first; and then they'll just continue later once it unblocks
         */
        esClient.bulk(request, RequestOptions.DEFAULT)

        //esClient.bulkAsync(request, RequestOptions.DEFAULT, getListener)
    }

    /**
      * Grabs an IndexInfoRequest and outputs the correct list of JSON (matching with which request type it received)
      *
      * Returning a List would be "prettier", but we're receiving Arrays from certain java functions and transforming
      * them to lists is a costly operation
      *
      * @param req the IndexInfoRequest
      * @return the Array of JSONs that matches the request
      */
    private def makeJson(req: IndexInfoRequest): Array[String] = req match {
        case req: AdminFile =>
            val json = jsonBuilder

            json.startObject()
            json.field("title", req.title)
            json.field("text", req.text)
            json.endObject()

            Array(Strings.toString(json))

        case req: AdminWord =>
            req.wordTags.map{ wordTag =>
                val json = jsonBuilder

                json.startObject()
                json.field("title", req.title)
                json.field("word", wordTag._1)
                json.field("tag", wordTag._2)
                json.endObject()

                Strings.toString(json)
            }

        case req: AdminFileWord =>
            val json = jsonBuilder

            json.startObject()
            json.field("title", req.title)
            json.field("text", req.text)

                json.startArray("words")

                req.wordTag.foreach{ wordTag =>
                    json.startObject().field("word", wordTag._1).field("tag", wordTag._2).endObject()
                }

                json.endArray()

            json.endObject()

            Array(Strings.toString(json))
    }

    /* Maybe useful if we use bulkAsync in the future
    //TODO probablement recevoir le future et indiquer son success selon rep ou fail
    private def getListener: ActionListener[BulkResponse] = {
        val listener = new ActionListener[BulkResponse] {
            override def onResponse(response: BulkResponse): Unit = {
                println("AGAAAAAAAAAAAAAAAAAAAAAAAAA")
            }

            override def onFailure(e: Exception): Unit = {
                println("BIG FAIL")
                e.printStackTrace()
            }
        }

        listener
    }*/
}
