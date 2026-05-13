---
title: Document Processing
---

# Document Processing

After a document is fetched and passes all filters, it enters the **Import pipeline** — a separate sub-system responsible for parsing content, enriching metadata, and reshaping documents before they are committed.

## The Import module

The Import module (`importer/`) is a standalone library that can be used independently of the crawler.
Inside a crawl, it is invoked automatically as stage 3 of the pipeline.

A document entering the Import pipeline has:

- A **reference** (URL or file path)
- **Raw content** (bytes)
- Initial **metadata** (HTTP headers, filesystem attributes, etc.)

The Import pipeline produces:

- **Parsed text** (extracted from PDF, DOCX, HTML, images, ...)
- **Enriched metadata** (normalized fields, added values, renamed keys)

## Parsers

Parsers convert raw binary content into text and metadata.
The default parser is Apache Tika, which handles hundreds of document types out of the box:

| Format                  | Extracted content                         |
| ----------------------- | ----------------------------------------- |
| HTML, XML               | Text, links, title, meta tags             |
| PDF                     | Text, author, creation date, pages        |
| DOCX, XLSX, PPTX        | Text, author, sheet names                 |
| Images (JPEG, PNG, ...) | EXIF metadata; text via OCR if configured |
| Emails (MSG, EML)       | Subject, sender, body, attachments        |

Custom parsers can be registered for formats Tika doesn't handle or when you need specialized extraction.

## Transformers

Transformers modify the document's metadata or content after parsing.
They run sequentially in the order they are configured.

Common transformers:

| Transformer           | What it does                                           |
| --------------------- | ------------------------------------------------------ |
| `ReplaceTransformer`  | Find/replace in metadata field values                  |
| `CopyTransformer`     | Copy a metadata field to a new name                    |
| `DeleteTransformer`   | Remove unwanted metadata fields                        |
| `ExternalTransformer` | Pipe the document through an external command          |
| `ScriptTransformer`   | Run a JavaScript or Groovy script against the document |

## Taggers

Taggers add new metadata fields to a document based on its content or context.

Examples:

- Extract a value from the document body using a regex and write it to a field
- Look up the document URL in a CSV file and add matching fields
- Classify document language and write it to a `language` field

## Content filters

Import-level filters can discard a document based on its _parsed content_ — after text is extracted.
This lets you reject documents based on their actual text, not just their URL or file type.

```yaml
contentFilters:
  - class: RegexContentFilter
    regex: "INTERNAL USE ONLY"
    onMatch: exclude
```

## Splitters

A splitter decomposes a single document into multiple logical sub-documents before committing.

Useful for:

- Splitting a large HTML page with multiple articles into individual documents
- Extracting each worksheet of an Excel file as a separate document
- Processing email attachments independently from the email body

## Configuration

Every Import module option is covered in the [Configuration Reference](https://crawlerconfig.norconex.com/docs).
The [Configuration Editor](https://crawlerconfig.norconex.com) lets you explore and configure parsers, transformers, and taggers visually.
