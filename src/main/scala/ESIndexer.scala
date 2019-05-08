import java.util.StringJoiner

import scala.collection.mutable

class ESIndexer {
    //https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-docs-index.html
    //https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-docs-index.html

    def index(info: (String, String, Array[(String, String)])): Unit = {

    }

    def makeJson(info: Any): String = info match {
        case info: (String, String, Array[(String, String)]) => { //admin case

            var json = new mutable.HashMap[String, String]

            json.put("title", info._1)
            json.put("text", info._2)

            var wordTagJson = new mutable.HashMap[String, String]

            val bidon: Unit = info._3.foreach{
                wordTag: (String, String) => wordTagJson.put(wordTag._1, wordTag._2)
            }

            json.put("words", wordTagJson.toString())

            json.toString()
        }
    }
}
