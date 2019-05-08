import java.util.StringJoiner

class ESIndexer {
    //https://www.elastic.co/guide/en/elasticsearch/client/java-api/current/java-docs-index.html

    def index(info: (String, String, Array[(String, String)])): Unit = {

    }

    def makeJson(info: Any): String = info match {
        case info: (String, String, Array[(String, String)]) => { //admin case
            var jsonBuilder = new StringBuilder("{title: \"")
            jsonBuilder.append(info._1).append("\", text: \"").append(info._2).append("\", words: { ")

            var stringJoiner = new StringJoiner()   //TODO!

            val bidon: Unit = info._3.foreach{
                wordTag: (String, String) => jsonBuilder.append("\"").append(wordTag._1).append("\": \"").append(wordTag._2).append("\", ")
            }

            jsonBuilder.de

            val wordsAndTags = info._3.foldLeft("") {
                (acc, wordTag: (String, String)) => {
                    acc + wordTag._1 + ": " + wordTag._2 + ", "
                }
            }

            json + wordsAndTags + "}}"
        }
    }
}
