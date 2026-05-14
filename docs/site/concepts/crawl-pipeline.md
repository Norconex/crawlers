---
title: Crawl Pipeline
---

# Crawl Pipeline

Every document processed by Norconex Crawler — whether a web page or a filesystem file — moves through the same four-stage pipeline.

```
Crawl  ──►  Filter  ──►  Transform  ──►  Commit
```

Understanding this pipeline is the key to configuring and extending the crawler effectively.

## Stage 1: Crawl (Fetch)

The crawler fetches documents from the configured sources.

- **Web Crawler**: Makes HTTP/S requests, follows links discovered in pages, respects `robots.txt` and `Crawl-Delay` directives.
- **Filesystem Crawler**: Traverses directory trees, connects to remote systems (FTP, SFTP, WebDAV, HDFS, S3).

After each fetch, the document's raw content and HTTP/filesystem metadata are available to subsequent stages.

### Queue management

Before fetching, each candidate URL or path passes through a **queue processor**:

- Already-visited references are skipped (deduplication by URL or checksum)
- Filtered-out references never enter the fetch queue
- The queue persists to disk, enabling crawl resume after interruptions

## Stage 2: Filter

Filters decide which documents proceed and which are discarded.

There are two categories:

| Type                 | Runs on                          | Examples                                 |
| -------------------- | -------------------------------- | ---------------------------------------- |
| **Reference filter** | The URL or path, before fetching | URL pattern, depth, domain               |
| **Document filter**  | The fetched content and metadata | content type, file size, custom metadata |

Filters are composable — you can stack multiple include/exclude rules.
A document is discarded as soon as any filter rejects it; no further processing occurs.

## Stage 3: Transform (Import Pipeline)

Accepted documents enter the **Import module**, which is a separate sub-pipeline:

1. **Parser** — extract text and metadata from raw content (PDF, DOCX, HTML, images via OCR, ...)
2. **Transformers** — enrich, reshape, or filter metadata fields
3. **Tagger** — apply additional field values (e.g., from a database lookup or regex extraction)
4. **Splitter** — optionally split a single document into multiple logical documents

The Import module is designed to be completely independent — you can use it outside the crawler to process files directly.

## Stage 4: Commit

The final stage sends the processed document to one or more **Committers**.

A committer is a plugin that knows how to write to a specific destination:
Elasticsearch, Solr, SQL, Kafka, Neo4j, Azure, and others.

Committers receive a standardized document object (reference, content, metadata map) and handle the destination-specific protocol.
Multiple committers can run in parallel for the same crawl session.

## Where to configure each stage

The [Configuration Editor](https://crawlerconfig.norconex.com) shows every option for every pipeline stage,
with inline documentation and sample values.
All stages are configured in a single config file.
