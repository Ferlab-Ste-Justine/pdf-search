import org.apache.http.HttpHost
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.elasticsearch.common.Strings
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.common.xcontent.XContentType

sealed trait IndexingRequest { //represents an IndexRequest into index of name getClass
    override def toString: String = this.getClass.getName
    def index: String
    def title: String
}

sealed abstract class AdminIndexRequest extends IndexingRequest

//TODO refactor trait into abstract class to use inIndex as super? (two sets of ())
//title-text
case class AdminFile(title: String, text: String, index: String = "adminfile") extends AdminIndexRequest
//title-word-tag
case class AdminWord(title: String, wordTags: Seq[(String, String)], index: String = "adminword") extends AdminIndexRequest
//title-text-words[word-tag]
case class AdminFileWord(title: String, text: String, words: Seq[String], index: String = "adminfileword") extends AdminIndexRequest

case class AdminFileLemma(title: String, text: String, words: Seq[String], index: String = "adminfilelemma") extends AdminIndexRequest

class ESIndexer(url: String = "http://localhost:9200") {
    //https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-docs-index.html
    //https://www.elastic.co/guide/en/elasticsearch/reference/7.0/docs-index_.html
    //https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
    //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-document-bulk.html

    val esClient = new RestHighLevelClient(RestClient.builder(HttpHost.create(url)))

    //initIndexes

    //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-create-index.html
    //https://discuss.elastic.co/t/elasticsearch-total-term-frequency-and-doc-count-from-given-set-of-documents/115223
    //https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-termvectors.html
    def initAdminIndexesEnglish: Unit = {
        val json = jsonBuilder

        json.startObject()
        json.startObject("properties")
            json.startObject("text")
                json.field("type", "text")
                json.field("analyzer", "english")
            json.endObject()
        json.endObject()
        json.endObject()

        val temp = new CreateIndexRequest("adminfile")
        temp.mapping(Strings.toString(json), XContentType.JSON)

        /*
        Try to create the index. If it already exists, don't do anything
         */
        try {
            esClient.indices().create(temp, RequestOptions.DEFAULT)
        } catch {
            case _: Exception =>
        }
    }

    def initAdminIndexesHunspell: Unit = {
        val jsonAdminFileLemma = jsonBuilder

        jsonAdminFileLemma.startObject()
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
            jsonAdminFileLemma.endObject()
        jsonAdminFileLemma.endObject()

        val request = new CreateIndexRequest("adminfilelemma")
        request.mapping(jsonAdminFileLemma)

        /*
        Try to create the index. If it already exists, don't do anything
         */
        try {
            esClient.indices().create(request, RequestOptions.DEFAULT)
        } catch {
            case e: Exception => e.printStackTrace()
        }
    }

    /**
      * Makes an ES IndexRequest that puts json into index
      *
      * @param index the target ES index
      * @param json the input for index
      * @return the IndexRequest
      */
    private def makeIndexRequest(req: IndexingRequest, json: String): IndexRequest = {
        val request = new IndexRequest(req.index)
        //request.id(req.title.replaceAll(" ", ""))
        request.source(json, XContentType.JSON)
    }

    /**
      * Makes a bulk request corresponding to a list of IndexInfoRequest
      *
      * @param requests the IndexInfoRequests
      */
    def bulkIndex(requests: List[AdminIndexRequest]): Unit = {

        val request = new BulkRequest()                             //creates a bulkRequest
        requests.foreach{ req =>                                    //for every IndexInfoRequest
            makeJson(req).foreach{ json =>                          //for every json from said request
                request.add(makeIndexRequest(req, json))   //add the the IndexRequest to the bulk request
            }
        }

        /*
        We could use bulkAsync; however, bulk allows us to work with Futures easier AND we're already going Async with
        said futures.

        Bulk will block the ones that get here first; and then they'll just continue later once it unblocks
         */
        esClient.bulk(request, RequestOptions.DEFAULT)
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
    def makeJson(req: AdminIndexRequest): Seq[String] = req match {
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

                req.words.foreach{ word =>
                    json.startObject().field("word", word).endObject()
                }

                json.endArray()

            json.endObject()

            Array(Strings.toString(json))
        case req: AdminFileLemma =>
            val json = jsonBuilder

            json.startObject()
            json.field("title", req.title)
            json.field("text", req.text)

            json.startArray("words")

            req.words.foreach{ word =>
                json.value(word)
            }

            json.endArray()

            json.endObject()

            println(Strings.toString(json))

            Array(Strings.toString(json))
    }
}
