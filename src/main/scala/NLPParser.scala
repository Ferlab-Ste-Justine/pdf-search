import java.io.{File, FileInputStream}
import java.util.regex.Pattern

import opennlp.tools.lemmatizer.DictionaryLemmatizer
import opennlp.tools.postag.{POSModel, POSTaggerME}
import opennlp.tools.tokenize.{TokenizerME, TokenizerModel}

import scala.collection.mutable.ArrayBuffer

class NLPParser(language: String = "en") {

    //https://www.tutorialspoint.com/opennlp/opennlp_finding_parts_of_speech.htm
    //TODO how can we use mutiple languages in the same model??

    /*
    Notes on thread-safety: openNLP is NOT thread-safe! The only thing we can share is the models.

    https://stackoverflow.com/questions/4989381/nullpointer-exception-with-opennlp-in-namefinderme-class
    https://stackoverflow.com/questions/22785942/nullpointerexception-while-using-postaggerme-of-opennlp-in-android

    The rest has to be instanciated on a thread-by thread basis
     */
    val posModel = new POSModel(new FileInputStream("./nlp/" +language+"-pos-maxent.bin"))
    val tokenModel = new TokenizerModel(new FileInputStream("./nlp/" +language+"-token.bin"))
    //https://raw.githubusercontent.com/richardwilly98/elasticsearch-opennlp-auto-tagging/master/src/main/resources/models/en-lemmatizer.dict
    val dictFile: File = new File("nlp/" +language+"-lemmatizer.dict")

    /*val blacklist: Set[String] = readBlackList

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

    def keywordLearn(lemmas: Seq[Seq[String]]): Map[String, Double] = {
        val dfMap = scala.collection.mutable.Map[String, Int]()
        lemmas.foreach{ lemmaList =>
            lemmaList.foreach{ lemma =>
                if(!dfMap.isDefinedAt(lemma)) dfMap += (lemma -> 1)
                else dfMap += (lemma -> (dfMap(lemma)+1))
            }
        }

        dfMap.foldLeft(Map[String, Double]()){ (acc, lemmaDF: (String, Int)) =>
            acc + (lemmaDF._1 -> Math.log( (lemmas.length + 1).asInstanceOf[Double] / (dfMap(lemmaDF._1).asInstanceOf[Double] + 1) ))
        }
    }

    def keywordTake(tfMap: Map[String, Int], idfMap: Map[String, Double], batch: Int = 10): List[String] = {
        tfMap.map{ lemmaTF =>
            (lemmaTF._1, lemmaTF._2 * idfMap(lemmaTF._1))

        }.toList.sortWith(_._2 > _._2).take(batch).map( tuple => tuple._1 )
    }

    def keywordLearnAndTake(texts: List[String]): Seq[List[String]] = {
        val tfMapList: Seq[Map[String, Int]] = texts.map{ text =>
            getLemmas(text).foldLeft(Map[String, Int]()){ (acc, lemma: String) =>
                if(!acc.isDefinedAt(lemma)) acc + (lemma -> 1)
                else acc + (lemma -> (acc(lemma)+1))
            }
        }

        val idfMap = keywordLearn( tfMapList.map(tfMap => tfMap.keys.toList) )

        tfMapList.map( tfMap => keywordTake( tfMap, idfMap) )
    }

    def getNouns(text: String): Array[String] = {

        /*
        We now have two Arrays: one has the text's tokens, the other the token's tags.

        We then filter the tokens by their tag, keeping only the key (most important) words

        OpenNLP has weird behaviour with some punctuation, so we're just removing it all from the results

        https://stackoverflow.com/questions/18814522/scala-filter-on-a-list-by-index
        https://stackoverflow.com/questions/4328500/how-can-i-strip-all-punctuation-from-a-string-in-javascript-using-regex
         */
        getTokenTags(text).collect {
            case (token, tag) if isKeytag(tag) =>
                token.replaceAll("[,\\/#!$%\\^&\\*;|:{}=\\-_`~()\\[\\]<>\"”(\\.$)]", "")

        }
    }*/

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
        val nounTag: (Array[String], Array[String]) = getTokenTags(text, nounsOnly = true).unzip
        //val nounTag = (Array("alcoholized", "agabaaegj"), Array("VBN", "NN"))

        val tempLemmas = new DictionaryLemmatizer(dictFile).lemmatize(nounTag._1, nounTag._2)

        /*
        When the lemmatizer can't find a word, it outputs "O". We want to replace the O's with their original words
        (found in nounTag).

        We could technically zip tempLemmas with nountag and then use foldLeft with a case match; but that would be
        pretty inefficient. Instead, we're going imperative-style and using the index of the O's to grab the word.
         */

        tempLemmas.indices.foldLeft(Set[String]()) { (acc: Set[String], i: Int) =>
            val lemma: String = tempLemmas(i)

            if(lemma.equals("")) acc    //lemmatizer bug? random empty strings
            else if(lemma.equals("O")) acc + nounTag._1(i)
            else acc + lemma
        }.toArray
    }

    /**
      * Returns every (NLP token, NLP tag) tuple from the provided text
      *
      * @param text the text to parse for tokentags
      * @return the zip of the tokens and tags from the text
      */
    def getTokenTags(text: String, nounsOnly: Boolean = false): Array[(String, String)] = {
        val tokens = tokenize(text)
        val tokenTags = tokens.zip(new POSTaggerME(posModel).tag(tokens))   //POSTaggerME is not thread safe...

        if(!nounsOnly) tokenTags
        else {
            /**
              * Is the tag an important tag?
              *
              * @param tag the tag
              * @return whether or not the tag is important
              */
            def isKeytag(tag: String): Boolean = tag match {
                //https://medium.com/@gianpaul.r/tokenization-and-parts-of-speech-pos-tagging-in-pythons-nltk-library-2d30f70af13b
                case "NNP" => true  //proper nouns
                case "NNPS" => true
                case "NN" => true   //nouns
                case "NNS" => true
                case "FW" => true   //foreign words
                case _ => false     //anything else is not a keyword
            }

            /**
              * Can this string be considered a number? / Is this a numeric string?
              * @param str the string
              * @return wether or not it's a number
              */
            def isNumeric(str: String): Boolean = {
                try {
                    Integer.valueOf(str)
                    true
                } catch {
                    case _: Exception => false
                }
            }

            /**
              * In our dataset the only interesting words of length two and lower are bone identifiers.
              *
              * @param str the word
              * @return wether or not it's a bone ID
              */
            def isBoneID(str: String): Boolean = Pattern.matches("[a-z][0-9]+", str)

            tokenTags.foldLeft(Array[(String, String)]()) { (acc, tokentag) =>
                val token = tokentag._1.replaceAll("[,\\/#!$%\\^&\\*;|:{}=\\-_`~()\\[\\]<>\"”(\\.$)]", "").toLowerCase
                val tag = tokentag._2

                if(isKeytag(tag) && (token.length>=3 || isBoneID(token)) && !isNumeric(token)) acc :+ (token, tag)
                else acc
            }
        }
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
}
