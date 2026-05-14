---
title: v3 to v4 Detailed Guide
---

# v3 → v4 Detailed Migration Guide

## Configuration structure

The top-level structure changed significantly.

**v3:**

```xml
<crawler id="my-crawl">
  <startURLs>
    <url>https://example.com</url>
  </startURLs>
  <numThreads>10</numThreads>
  ...
</crawler>
```

**v4 (YAML):**

```yaml
crawlerDefaults:
  numThreads: 10

crawlers:
  - id: my-crawl
    startUrls:
      - https://example.com
```

Key changes:

- `<crawler>` becomes an item in the `crawlers` list
- Shared settings move to `crawlerDefaults`
- `startURLs/url` becomes `startUrls` (flat list)
- `numThreads` moves to `crawlerDefaults` (or per-crawler)

## Module / artifact renaming

| v3 artifact                  | v4 artifact                              |
| ---------------------------- | ---------------------------------------- |
| `nx-collector-core`          | `nx-crawler-core`                        |
| `nx-collector-http`          | `nx-crawler-web`                         |
| `nx-collector-filesystem`    | `nx-crawler-fs`                          |
| `nx-committer-core`          | `nx-committer-core` (unchanged)          |
| `nx-committer-elasticsearch` | `nx-committer-elasticsearch` (unchanged) |

## Committer configuration

**v3:**

```xml
<committer class="com.norconex.committer.elasticsearch.ElasticsearchCommitter">
  <nodes>http://localhost:9200</nodes>
  <indexName>my-index</indexName>
</committer>
```

**v4:**

```yaml
committers:
  - class: com.norconex.committer.elasticsearch.ElasticsearchCommitter
    nodes: http://localhost:9200
    indexName: my-index
```

## Filter migration

| v3 class                | v4 class                      |
| ----------------------- | ----------------------------- |
| `ExtensionURLFilter`    | `ExtensionReferenceFilter`    |
| `RegexURLFilter`        | `RegexReferenceFilter`        |
| `SegmentCountURLFilter` | `SegmentCountReferenceFilter` |
| `DomainURLFilter`       | `DomainReferenceFilter`       |

The v3 `onMatch="exclude"` attribute maps directly to `onMatch: exclude` in v4 YAML.

## Link extraction

**v3:**

```xml
<linkExtractors>
  <extractor class="com.norconex.collector.http.link.impl.HtmlLinkExtractor">
    <tags><tag name="a" attribute="href"/></tags>
  </extractor>
</linkExtractors>
```

**v4:**

```yaml
linkExtractors:
  - class: HtmlLinkExtractor
    tags:
      - tagName: a
        attribute: href
```

## Java API

The v3 `Collector`/`Crawler` split is replaced by a single class per crawl type:

| v3                          | v4                             |
| --------------------------- | ------------------------------ |
| `new HttpCollector(config)` | `new WebCrawler()`             |
| `collector.start(false)`    | `crawler.start(config)`        |
| `HttpCollectorConfig`       | `WebCrawlerConfig`             |
| `HttpCrawlerConfig`         | folded into `WebCrawlerConfig` |

## Importer / transformer migration

The Import module class names are largely the same.
The main change is how transformers are configured: v3 XML attributes become YAML fields.

Use the [Configuration Editor](https://crawlerconfig.norconex.com) to verify each class name —
it shows both the current v4 name and any renamed v3 equivalents.

## Unsupported v3 features

A small number of v3 features were removed or replaced in v4:

| v3 feature                               | v4 status                               |
| ---------------------------------------- | --------------------------------------- |
| `DelayResolver` via XML sleep            | Use `crawlDelay` with ISO-8601 duration |
| Groovy-scripted filters (pre-Import)     | Use `ScriptFilter` with updated syntax  |
| Custom `ICrawlDataStore` implementations | Use the new `IDataStoreEngine` SPI      |

If you encounter a v3 feature not covered here, open a [GitHub Discussion](https://github.com/Norconex/crawlers/discussions).
