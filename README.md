# clin-pdf-search
Takes PDFs and outputs their text representation

HOW-TO:

1. Install Tesseract https://github.com/tesseract-ocr/tesseract on system
2. Make sure the tessdata and nlp folders are there (needed for OCR and NLP, respectively)
3. SBT Sync
4. Good to go!

Kibana commands backup

GET _search
{
  "query": {
    "match_all": {}
  }
}

GET /_cat/indices?v

GET /adminword/_search/?size=1000&pretty=1

GET /admin/adminlemma/_search/?size=1000&pretty=1

GET /adminfile/_search?q=*

GET /adminfile/_mapping

GET /adminfilewordlemma/_search?q=*

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

DELETE /adminword

DELETE /adminfile

DELETE /adminlemma

DELETE /adminfilewordlemma

GET /adminword/_search?q=*
