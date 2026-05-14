---
title: Extending the Crawler
---

# Extending the Crawler

Norconex Crawler is designed for extension.
Every major component — parsers, filters, transformers, committers, fetchers — is a plugin point.
You can add custom behavior without forking or modifying the core.

## Extension model

The crawler uses a **Service Provider Interface (SPI)** model backed by Java's standard `ServiceLoader`.

To register a custom class:

1. Implement the relevant interface (e.g., `IDocumentFilter`, `ICommitter`)
2. Annotate it with `@PolymorphicType` so it can be referenced by class name in config files
3. Register it in `META-INF/services/` or via a `PolymorphicTypeProvider` implementation

Once registered, your class appears in the [Configuration Editor](https://crawlerconfig.norconex.com)
and can be used in any config file like a built-in class.

## Common extension points

### Custom filter

```java
@PolymorphicType
public class PricePageFilter implements IDocumentFilter {

    @Override
    public boolean acceptDocument(CrawlDoc doc) {
        // only crawl pages with "pricing" in their URL
        return doc.getReference().contains("pricing");
    }
}
```

Reference it in your config:

```yaml
documentFilters:
  - class: com.example.PricePageFilter
```

### Custom transformer

```java
@PolymorphicType
public class UppercaseTitleTransformer implements IDocumentTransformer {

    @Override
    public void transformDocument(CrawlDoc doc) {
        String title = doc.getMetadata().getString("title");
        if (title != null) {
            doc.getMetadata().set("title", title.toUpperCase());
        }
    }
}
```

### Custom committer

Implement `AbstractCommitter` or `ICommitter` to send documents to any destination:

```java
@PolymorphicType
public class MyApiCommitter extends AbstractCommitter {

    @Override
    protected void doUpsert(CommitterRequest req) {
        myApiClient.index(req.getReference(), req.getContent(), req.getMetadata());
    }

    @Override
    protected void doDelete(CommitterRequest req) {
        myApiClient.delete(req.getReference());
    }
}
```

## Event listeners

The crawler fires events throughout its lifecycle that you can react to without implementing a full plugin:

```java
crawler.getEventManager().addListener(event -> {
    if (event instanceof DocumentRejectedEvent e) {
        log.warn("Rejected: {} — {}", e.getCrawlDoc().getReference(), e.getReason());
    }
});
```

Key event types:

| Event                    | When it fires                          |
| ------------------------ | -------------------------------------- |
| `CrawlerStartedEvent`    | Crawl session begins                   |
| `CrawlerStoppedEvent`    | Crawl session ends                     |
| `DocumentFetchedEvent`   | After a document is fetched            |
| `DocumentImportedEvent`  | After the Import pipeline completes    |
| `DocumentCommittedEvent` | After a committer accepts the document |
| `DocumentRejectedEvent`  | When a filter discards a document      |

## Packaging extensions

Package your extension classes in a JAR and drop it into the `lib/` directory of the crawler distribution.
No code changes to the core are required.

For Maven projects, declare a dependency on the crawler module and the `importer` module.
Your JAR will be picked up automatically at runtime.

## Resources

- [Configuration Reference](https://crawlerconfig.norconex.com/docs) — all built-in extension points with examples
- [GitHub: crawlers](https://github.com/Norconex/crawlers) — source code and existing implementations to use as reference
