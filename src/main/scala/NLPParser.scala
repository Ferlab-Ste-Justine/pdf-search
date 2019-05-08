import java.io.{FileInputStream, InputStream}

import opennlp.tools.postag.{POSModel, POSSample, POSTaggerME}
import opennlp.tools.tokenize.WhitespaceTokenizer

class NLPParser(language: String = "en") {

    //https://www.tutorialspoint.com/opennlp/opennlp_finding_parts_of_speech.htm
    //TODO how can we use mutiple languages in the same model??
    val posTagger =  new POSTaggerME(new POSModel(new FileInputStream("./nlp/"+language+"-pos-maxent.bin")))
    val tokenizer: WhitespaceTokenizer = WhitespaceTokenizer.INSTANCE

    /**
      * Extracts the words having interesting NLP tags from the given text
      *
      * @param text The text to parse
      * @return An Array of its NLP keywords
      */
    def getNouns(text: String): Array[String] = {
        /*
        We now have two Arrays: one has the text's tokens, the other the token's tags.

        We then filter the tokens by their tag, keeping only the key (most important) words
         */

        //https://stackoverflow.com/questions/18814522/scala-filter-on-a-list-by-index
        //https://stackoverflow.com/questions/4328500/how-can-i-strip-all-punctuation-from-a-string-in-javascript-using-regex
        getTokenTags(text).collect {
            case (token, tag) if isKeytag(tag) =>
                token.replaceAll("[,\\/#!$%\\^&\\*;|:{}=\\-_`~()\\[\\]<>\"”(\\.$)]", "")
        }
    }

    def getTokenTags(text: String): Array[(String, String)] = {
        val tokens = tokenize(text)
        tokens.zip(posTagger.tag(tokens))
    }

    private def tokenize(text: String): Array[String] =
        //the tagger doesn't like ellipses, so we replace them with simple dots before the filter below
        tokenizer.tokenize(text).map(x => x.replaceAll("\\.\\.", ""))

    /**
      * Is the tag an important tag?
      *
      * @param tag the tag
      * @return whether or not the tag is important
      */
    private def isKeytag(tag: String): Boolean = tag match {
        //https://medium.com/@gianpaul.r/tokenization-and-parts-of-speech-pos-tagging-in-pythons-nltk-library-2d30f70af13b
        case "NNP" => true  //proper nouns
        case "NNPS" => true
        case "NN" => true   //nouns
        case "NNS" => true
        case "FW" => true   //foreign words
        case _ => false     //anything else is not a keyword
    }
}
