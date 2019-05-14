import java.io.{BufferedReader, File, FileInputStream, FileReader, InputStream}

import opennlp.tools.lemmatizer.DictionaryLemmatizer
import opennlp.tools.postag.{POSModel, POSSample, POSTaggerME}
import opennlp.tools.tokenize.{TokenizerME, TokenizerModel, WhitespaceTokenizer}

import scala.collection.mutable

class NLPParser(language: String = "en") {

    //https://www.tutorialspoint.com/opennlp/opennlp_finding_parts_of_speech.htm
    //TODO how can we use mutiple languages in the same model??

    /*
    Notes on thread-safety: openNLP is NOT thread-safe! The only thing we can share is the models.

    https://stackoverflow.com/questions/4989381/nullpointer-exception-with-opennlp-in-namefinderme-class
    https://stackoverflow.com/questions/22785942/nullpointerexception-while-using-postaggerme-of-opennlp-in-android

    The rest has to be instanciated on a thread-by thread basis
     */
    val posModel = new POSModel(new FileInputStream("./nlp/"+language+"-pos-maxent.bin"))
    val tokenModel = new TokenizerModel(new FileInputStream("./nlp/"+language+"-token.bin"))
    //https://raw.githubusercontent.com/richardwilly98/elasticsearch-opennlp-auto-tagging/master/src/main/resources/models/en-lemmatizer.dict
    val dictFile: File = new File("./nlp/"+language+"-lemmatizer.dict")

    val blacklist: Set[String] = readBlackList

    def readBlackList: Set[String] = {

        val set: mutable.Set[String] = scala.collection.mutable.Set()
        val reader = new BufferedReader(new FileReader("./words/blacklist.tsv"))

        var line = reader.readLine()
        while(line != null) {
            set.add(line)
            line = reader.readLine()
        }

        reader.close()

        set.toSet
    }

    def keywordise(text: String): Array[String] = {
        keywordise(getTokenTags(text))
    }

    def keywordise(tokenTags: Array[(String, String)]): Array[String] = {
        tokenTags.collect {
            case (token, tag) if isKeytag(tag) && !blacklist.contains(token) =>
                token.replaceAll("[,\\/#!$%\\^&\\*;|:{}=\\-_`~()\\[\\]<>\"”(\\.$)]", "")
        }
    }

    /**
      * Extracts the words having interesting NLP tags from the given text
      *
      * @param text The text to parse
      * @return An Array of its NLP keywords
      */
    def getNounTags(text: String): (Array[String], Array[String]) = {

        /*
        We now have two Arrays: one has the text's tokens, the other the token's tags.

        We then filter the tokens by their tag, keeping only the key (most important) words

        OpenNLP has weird behaviour with some punctuation, so we're just removing it all from the results

        https://stackoverflow.com/questions/18814522/scala-filter-on-a-list-by-index
        https://stackoverflow.com/questions/4328500/how-can-i-strip-all-punctuation-from-a-string-in-javascript-using-regex
         */
        getTokenTags(text).collect {
            case (token, tag) if isKeytag(tag) =>
                (token.replaceAll("[,\\/#!$%\\^&\\*;|:{}=\\-_`~()\\[\\]<>\"”(\\.$)]", ""), tag)

        }.unzip
    }

    /**
      * Gets the lemmas of the text (useful for keywordisation).
      *
      * Lemmas are the generic forms of words
      *
      * https://nlp.stanford.edu/IR-book/html/htmledition/stemming-and-lemmatization-1.html
      *
      * @param text the input text
      * @return the lemmas of it's NN/NNS
      */
    def getLemmas(text: String): Array[String] = {
        val nounTag = getNounTags(text)

        new DictionaryLemmatizer(dictFile).lemmatize(nounTag._1, nounTag._2)
    }


    /**
      * Returns every (NLP token, NLP tag) tuple from the provided text
      *
      * @param text the text to parse for tokentags
      * @return the zip of the tokens and tags from the text
      */
    def getTokenTags(text: String): Array[(String, String)] = {
        val tokens = tokenize(text)
        tokens.zip(new POSTaggerME(posModel).tag(tokens))   //POSTaggerME is not thread safe...
    }

    /**
      * Gets the token from the provided text
      *
      * @param text the text to tokenize
      * @return the text's tokens
      */
    private def tokenize(text: String): Array[String] =
        //the tokenizer doesn't like ellipses, so we replace them with simple dots before the filter below
        new TokenizerME(tokenModel).tokenize(text).map(x => x.replaceAll("(\\.\\.)", ""))

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
