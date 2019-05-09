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

GET /adminword/_search?q=*

GET /adminfile/_search?q=*

GET /adminfileword/_search?q=*

GET /admin_pdf/_search
{
    "_source": false,
    "query" : {
        "nested" : {
          "path": "words",
          "query": {
            "term" : { "tag" : "NN" }}
        }
    }
}

GET /admin_pdf/_mapping

DELETE /admin_pdf

GET /adminword/_search?q=*
