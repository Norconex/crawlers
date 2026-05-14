---
title: Concepts
sidebar_label: Overview
slug: /concepts
---

# Concepts

This section explains how Norconex Crawler works conceptually.
Understanding these ideas will help you configure and extend the crawler effectively.

## Topics in this section

| Topic                                           | What it covers                                       |
| ----------------------------------------------- | ---------------------------------------------------- |
| [Crawl Pipeline](./crawl-pipeline.md)           | How documents move from source to destination        |
| [Sessions](./sessions.md)                       | Resumable crawl state, deduplication, and scheduling |
| [Document Processing](./document-processing.md) | The Import module: parsing, enrichment, metadata     |
| [Extending the Crawler](./extending.md)         | Custom components, SPI, event listeners              |

## The big picture

```
 Source                 Pipeline                           Destination
 ──────            ─────────────────────────────────       ───────────
 Web     ──────►  Fetch → Queue → Filter → Import → Commit ──────►  Elasticsearch
 Files   ──────►                                            ──────►  Solr
                                                            ──────►  Kafka, SQL, ...
```

Every document — whether a web page or a file — passes through the same pipeline.
The crawl type (Web vs. Filesystem) only affects the **Fetch** stage.
Everything downstream is identical.

## Configuration model

All configuration lives in a single file (XML, YAML, or JSON).
The [Configuration Editor](https://crawlerconfig.norconex.com) provides a visual way to build and validate configs,
with a full searchable reference for every option.
