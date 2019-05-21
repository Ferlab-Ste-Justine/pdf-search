# clin-pdf-search
Takes PDFs and outputs their text representation

HOW-TO:

1. Install Tesseract https://github.com/tesseract-ocr/tesseract on system
2. Make sure the tessdata and nlp folders are there (needed for OCR and NLP, respectively)
3. SBT Sync
4. Good to go!

Kibana commands backup

GET /_cat/indices?v

GET /adminword/_search/?size=1000&pretty=1

GET /adminfile/_search?q=*

GET /adminfile/_mapping

GET /adminfileword/_mapping

GET /adminfile/_search/?size=1000&pretty=1
{
  "query": {
    "match": {
      "text": "motARechercher"
    }
  }
}

POST /adminfile/_mtermvectors
{
    "ids" : ["NomDeLetude"],
    "parameters": {
        "fields": [
                "text"
        ],
        "term_statistics": true,
        "filter" : {
      "max_num_terms" : 10,
      "min_term_freq" : 1,
      "min_doc_freq" : 1
    }
    }
}

GET /adminfileword/_search/?size=1000&pretty=1
{
  "query": {
    "match": {
      "text": "motARechercher"
    }
  }
}

POST /adminfileword/_mtermvectors
{
    "ids" : ["NomDeLetude"],
    "parameters": {
        "fields": [
                "text"
        ],
        "term_statistics": true,
        "filter" : {
      "max_num_terms" : 10,
      "min_term_freq" : 1,
      "min_doc_freq" : 1
    }
    }
}


DELETE /adminword

DELETE /adminfile

DELETE /adminlemma

DELETE /adminfileword

GET /adminword/_search?q=*
