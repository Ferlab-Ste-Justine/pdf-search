import org.apache.http.HttpHost
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.{SearchRequest, SearchResponse, SearchType}
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.elasticsearch.common.Strings
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentFactory._
import org.elasticsearch.common.xcontent.{ToXContent, XContentBuilder, XContentType}
import org.elasticsearch.index.query.{BoolQueryBuilder, MatchAllQueryBuilder, QueryBuilder, QueryBuilders}
import org.elasticsearch.search.aggregations.{AggregationBuilder, AggregationBuilders}
import org.elasticsearch.search.aggregations.bucket.terms.{ParsedStringTerms, Terms, TermsAggregationBuilder}
import org.elasticsearch.search.builder.SearchSourceBuilder
import shapeless.ops.zipper.First

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, _}
import scala.util.parsing.json.JSONObject


sealed trait IndexingRequest { //represents an IndexRequest into index of name getClass
    override def toString: String = this.getClass.getName
    def index: String
    def title: String
}

case class FileLemmas(title: String, text: String, words: Iterable[String], index: String = "filelemmas") extends IndexingRequest

class ESIndexer(url: String = "http://localhost:9200") {
    //https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-docs-index.html
    //https://www.elastic.co/guide/en/elasticsearch/reference/7.0/docs-index_.html
    //https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
    //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-document-bulk.html

    val esClient = new RestHighLevelClient(RestClient.builder(HttpHost.create(url)))

    private def makeIndexRequest(req: FileLemmas) = {
        val request = new IndexRequest(req.index)
        request.source(makeJson(req))

        request
    }

    def search(words: List[String] = List()): String = {
        //https://www.elastic.co/guide/en/elasticsearch/client/java-rest/master/java-rest-high-search.html

        val search = new SearchRequest("filelemmas")

        val builder: SearchSourceBuilder = new SearchSourceBuilder()

        def queryBuilder(words: List[String]): QueryBuilder = {

            @tailrec
            def iter(words: List[String], queryBuilder: BoolQueryBuilder): BoolQueryBuilder = words match {
                case List() =>
                    queryBuilder
                case head :: tail =>
                    iter(tail, queryBuilder.must(QueryBuilders.termQuery("words", head)))
            }

            if(words.isEmpty) QueryBuilders.matchAllQuery()
            else iter(words, QueryBuilders.boolQuery())
        }

        builder.query(queryBuilder(words))

        val aggs: TermsAggregationBuilder = AggregationBuilders.terms("words").field("words").size(50)

        builder.aggregation(aggs)
        builder.size(0)
        search.source(builder)

        val resp: SearchResponse = esClient.search(search, RequestOptions.DEFAULT)

        parseSearchResponse(resp)
    }

    private def parseSearchResponse(resp: SearchResponse): String = {
        val agg: Terms = resp.getAggregations.get("words")

        val json = jsonBuilder
        agg.getBuckets


        json.startObject()

            json.startArray("words")
            agg.getBuckets.forEach{ bucket =>
                json.startArray()
                    .value(bucket.getKeyAsString)
                    .value(bucket.getDocCount)
                .endArray()
            }
            json.endArray()


        Strings.toString(json.endObject())
    }

    def makeJson(req: IndexingRequest): XContentBuilder = req match {
        case req: FileLemmas =>
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

            json
    }
}
