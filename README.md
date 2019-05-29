# clin-pdf-search
Takes PDFs and outputs their text representation

HOW-TO:

1. Install Tesseract https://github.com/tesseract-ocr/tesseract on system
2. Make sure the tessdata and nlp folders are there (needed for OCR and NLP, respectively)
3. SBT Sync
4. Good to go!

Kibana commands backup

GET /_cat/indices?v

GET /filelemmas/_mapping

GET /filelemmas/_search
{
    "size": 0,
    "aggs" : {
        "text" : {
            "terms": {
                "field" : "words",
                "size": 1000
            }
        }
    }
}

GET /filelemmas/_search
{
    "query": {
      "bool": {
        "must": [
          {"term": {"words": "brain"}},
          {"term": {"words": "page"}},
          {"term": {"words": "biopsy"}}
        ]
      }
    },
    "size": 0,
    "aggs" : {
        "text" : {
            "terms": {
                "field" : "words",
                "size": 1000
            }
        }
    }
}

GET /filelemmas/_search
{
    "query" : {
        "match" : {"text" : "brain"}
    },
    "_source":false,
    "highlight": {
        "fields": {
            "text": {}
        }
    }
}

GET /filelemmas/_search
{
    "size": 0,
    "aggs" : {
        "text_terms" : {
            "terms" : {
                "field" : "words",
                "order" : { "_count" : "desc" },
                "size": 10
            },
            "aggregations": {
        "related_words" : {
            "significant_text" : {
                "field" : "text",
                "size": 10
            }
        }
    }
        }
    }
}

GET /filelemmas/_search
{
    "query" : {
        "match" : {"text" : "of"}
    },
    "_source":false,
    "aggregations" : {
        "my_sample" : {
            "sampler" : {
                "shard_size" : 100
            },
            "aggregations": {
                "keywords" : {
                    "significant_text" : { "field" : "text"}
                }
            }
        }
    },
    "highlight": {
        "fields": {
            "text": {}
        }
    }
}

POST /filelemmas/_mtermvectors
{
    "ids" : ["YaI26moBUd_NFizDVCxm"],
    "parameters": {
        "fields": [
                "text"
        ],
        "term_statistics": true,
        "offsets" : false,
  "payloads" : false,
  "positions" : false,
        "filter" : {
      "max_num_terms" : 1000,
      "min_term_freq" : 1,
      "min_doc_freq" : 1
    }
    }
}

POST /filelemmas/_mtermvectors
{
    "ids" : ["YaI26moBUd_NFizDVCxm", "W6I26moBUd_NFizDQyzn"],
    "parameters": {
        "fields": [
                "text"
        ],
        "term_statistics": true,
        "offsets" : false,
  "payloads" : false,
  "positions" : false,
        "filter" : {
      "max_num_terms" : 10,
      "min_term_freq" : 1,
      "min_doc_freq" : 1
    }
    }
}

DELETE /filelemmas
