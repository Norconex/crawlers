---
title: Introduction
sidebar_label: Introduction
slug: /getting-started
---

# Welcome to Norconex Crawler

Norconex Crawler is an open-source, enterprise-grade crawling framework that collects content
from **websites** and **filesystems** and delivers it to search engines, databases, and data
pipelines — without vendor lock-in.

## Two crawlers, one framework

|                      | Web Crawler                   | Filesystem Crawler                      |
| -------------------- | ----------------------------- | --------------------------------------- |
| **Sources**          | HTTP/S websites               | Local disk, FTP, SFTP, WebDAV, HDFS, S3 |
| **Key capability**   | Link extraction, JS rendering | Path and extension filtering            |
| **Typical use case** | Web indexing, site monitoring | Document management, NAS indexing       |

Both crawlers share the same configuration model, pipeline, and committer system.
Learn one, know both.

## Core concepts at a glance

- **Crawl session** — a named, resumable run with persistent state
- **Pipeline** — every document flows through: Crawl → Filter → Transform → Commit
- **Committers** — pluggable output targets (Elasticsearch, Solr, Kafka, SQL, Neo4j, ...)
- **Configuration Editor** — visual tool to create, edit, and validate configs in XML, YAML, or JSON

## Where to go next

- **New to Norconex?** → [Installation](./installation.md) then [Web Quick Start](./quick-start-web.md) or [Filesystem Quick Start](./quick-start-fs.md)
- **Embedding in Java?** → [Java Integration](./java-integration.md)
- **How does it all work?** → [Concepts](../concepts/index.md)
- **Upgrading from v3?** → [Migration Guide](../migration/index.md)
