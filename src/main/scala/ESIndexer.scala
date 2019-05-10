import java.util.StringJoiner

import org.apache.http.HttpHost
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.bulk.{BulkRequest, BulkResponse}
import org.elasticsearch.action.index.{IndexRequest, IndexResponse}
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.elasticsearch.common.Strings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.common.xcontent.XContentType

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

sealed abstract class IndexInfoRequest(info: Any) {
    override def toString: String = this.getClass.getName
    def getIndex: String = this.getClass.getName.toLowerCase
}

case class AdminFile(title: String, text: String) extends IndexInfoRequest
case class AdminWord(title: String, wordTags: Array[(String, String)]) extends IndexInfoRequest
case class AdminFileWord(title: String, text: String, wordTag: Array[(String, String)]) extends IndexInfoRequest

class ESIndexer(url: String = "http://localhost:9200") {
    //https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-docs-index.html
    //https://www.elastic.co/guide/en/elasticsearch/reference/7.0/docs-index_.html
    //https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
    //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-document-bulk.html

    val esClient = new RestHighLevelClient(RestClient.builder(HttpHost.create(url)))

    private def makeIndexRequest(index: String, json: String): IndexRequest = {
        val request = new IndexRequest(index)
        request.source(json, XContentType.JSON)

        request
    }

    def bulkIndex(requests: List[IndexInfoRequest]): Unit = {
        val request = new BulkRequest()
        requests.foreach{ req =>
            makeJson(req).foreach{ json =>
                request.add(makeIndexRequest(req.getIndex, json))
            }
        }

        /*
        We could use bulkAsync; however, bulk allows us to work with Futures easier AND we're already going Async with
        said futures.

        Bulk with block the ones that get here first; and then they'll just continue later once it unblocks
         */
        esClient.bulk(request, RequestOptions.DEFAULT)

        //esClient.bulkAsync(request, RequestOptions.DEFAULT, getListener)
    }

    /**
      * Grabs an IndexInfoRequest and outputs the correct JSON (matching with which request type it received)
      *
      * Returning a List would be "Prettier", but we're receiving Arrays from certain java functions and transforming
      * them to lists is a semi-costly operation
      *
      * @param req the IndexInfoRequest
      * @return the Array of JSONs that match the request
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

        /*
        case info: (String, String, Array[(String, String)]) => //admin case

            val json = jsonBuilder

            json.startObject()
            json.field("title", info._1)
            json.field("text", info._2)

            json.startArray("words")

            info._3.foreach{ wordTag =>
                json.startObject().field("word", wordTag._1).field("tag", wordTag._2).endObject()
            }

            json.endArray()
            json.endObject()

            println(Strings.toString(json))
            Strings.toString(json)


            val jsonBuilder = getJsonBuilder
            jsonBuilder.add(p("title", info._1)).add(p("text", info._2))

            val subJsonBuilder = getJsonBuilder
            info._3.foreach{
                wordTag => subJsonBuilder.add(p(wordTag._1, wordTag._2))
            }

            jsonBuilder.add("\"words\": "+subJsonBuilder.toString)

            println(jsonBuilder.toString)
            jsonBuilder.toString*/

    }

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
    }
}
