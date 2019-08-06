# Dockerfile

# Used to start from openjdk 11, but openjdk11 is on an old version of ubuntu and it won't install the right version of
# tesseract-ocr, leading to breaking bugs... so we're stuck with a huge docker until we have an alternative
FROM ubuntu:19.04

USER root:root

RUN apt-get update -y && apt-get upgrade -y && apt install openjdk-11-jdk -y && apt install -y tesseract-ocr

WORKDIR /workdir

COPY ./classes/artifacts/clin_pdf_search_jar/clin-pdf-search.jar .

COPY ./tessdata tessdata

RUN ls

ENTRYPOINT ["java", "-jar", "clin-pdf-search.jar"]

# https://stackoverflow.com/questions/41185591/jar-file-with-arguments-in-docker