# pdf-search
Takes PDFs and outputs their text representation

## Licence

Copyright (C) 2019 Centre de recherche du CHU Sainte-Justine
See LICENCE.txt

## HOW-TO

1. Install Tesseract https://github.com/tesseract-ocr/tesseract on system
2. Make sure the tessdata folder is there (needed for OCR)
3. SBT Sync
4. Good to go!

Call the program with -h to see help tips.

Takes a list of space-separated Study IDs (KF_STUDY_ID) and a release id (KF_RELEASE_ID) in env vars.
