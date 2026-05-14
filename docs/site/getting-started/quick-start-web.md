---
title: Web Crawler Quick Start
---

# Web Crawler Quick Start

This guide gets you from zero to a running web crawl in under 5 minutes.

## Step 1 — Create a config file

Create `my-web-crawl.yaml` with the following minimal configuration:

```yaml
crawlerDefaults:
  numThreads: 5
  maxDepth: 3
  committers:
    - class: com.norconex.committer.core.impl.LogCommitter

crawlers:
  - id: my-first-crawl
    startUrls:
      - https://example.com
```

This crawl will:

- Start at `https://example.com`
- Follow links up to 3 levels deep
- Log each document it processes (the `LogCommitter` is built-in and perfect for testing)

## Step 2 — Start the crawl

```bash
crawler-cli.sh start -config my-web-crawl.yaml
```

You'll see log output as pages are fetched, filtered, and committed.

## Step 3 — Stop and resume

Stop the crawl at any time with `Ctrl+C`. Norconex Crawler saves its state to disk automatically.

Resume exactly where you left off:

```bash
crawler-cli.sh start -config my-web-crawl.yaml
```

## Step 4 — Send to a real target

Replace the `LogCommitter` with your actual destination.

**Elasticsearch:**

```yaml
committers:
  - class: com.norconex.committer.elasticsearch.ElasticsearchCommitter
    nodes: http://localhost:9200
    indexName: my-website
```

**Apache Solr:**

```yaml
committers:
  - class: com.norconex.committer.solr.SolrCommitter
    solrURL: http://localhost:8983/solr/my-core
```

See the [Integrations](/integrations) page for all available committers and their configuration.

## CLI reference

| Command                | Description                                |
| ---------------------- | ------------------------------------------ |
| `start -config <file>` | Start or resume a crawl                    |
| `stop -config <file>`  | Gracefully stop a running crawl            |
| `clean -config <file>` | Delete crawl state (forces a full recrawl) |
| `info -config <file>`  | Print crawl status                         |

## Next steps

- Use the [Configuration Editor](https://crawlerconfig.norconex.com) to build your config visually
- Read [Concepts: Crawl Pipeline](../concepts/crawl-pipeline) to understand how documents are processed
- See [Concepts: Sessions](../concepts/sessions) to understand resume, deduplication, and scheduling
