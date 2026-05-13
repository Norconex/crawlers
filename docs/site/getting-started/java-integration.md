---
title: Java Integration
---

# Java Integration

The Norconex Crawler is designed to be embedded directly in Java applications.
This page covers three common integration patterns.

## Maven dependencies

Add the crawler(s) you need to your `pom.xml`:

```xml
<!-- Web Crawler -->
<dependency>
  <groupId>com.norconex.crawler</groupId>
  <artifactId>nx-crawler-web</artifactId>
  <version>4.x.x</version>
</dependency>

<!-- Filesystem Crawler -->
<dependency>
  <groupId>com.norconex.crawler</groupId>
  <artifactId>nx-crawler-fs</artifactId>
  <version>4.x.x</version>
</dependency>
```

For committer dependencies, see the [Integrations](/integrations) page.

## Pattern 1 — Load config from file and run

The simplest integration: point the crawler at a YAML/XML config file and run it programmatically.

```java
import com.norconex.crawler.web.WebCrawler;

public class MyCrawlerApp {
    public static void main(String[] args) throws Exception {
        WebCrawler crawler = new WebCrawler();
        crawler.start("path/to/my-crawl.yaml");
    }
}
```

## Pattern 2 — Configure programmatically

Build the entire configuration in code without a config file:

```java
import com.norconex.crawler.web.WebCrawler;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.committer.elasticsearch.ElasticsearchCommitter;

public class MyCrawlerApp {
    public static void main(String[] args) throws Exception {
        var esCommitter = new ElasticsearchCommitter();
        esCommitter.setNodes("http://localhost:9200");
        esCommitter.setIndexName("my-content");

        var config = new WebCrawlerConfig();
        config.setId("my-crawl");
        config.setStartURLs(List.of("https://example.com"));
        config.setNumThreads(10);
        config.setCommitters(List.of(esCommitter));

        var crawler = new WebCrawler();
        crawler.start(config);
    }
}
```

## Pattern 3 — Event-driven integration

React to crawl lifecycle events to integrate with your application's monitoring or workflow:

```java
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.event.impl.CrawlerStartedEvent;
import com.norconex.crawler.core.event.impl.CrawlerStoppedEvent;
import com.norconex.crawler.core.event.impl.DocumentCommittedEvent;

var crawler = new WebCrawler();
crawler.getEventManager().addListener(event -> {
    if (event instanceof CrawlerStartedEvent e) {
        System.out.println("Crawl started: " + e.getSource().getId());
    } else if (event instanceof DocumentCommittedEvent e) {
        System.out.println("Committed: " + e.getCrawlDoc().getReference());
    } else if (event instanceof CrawlerStoppedEvent e) {
        System.out.println("Crawl finished.");
    }
});
crawler.start(config);
```

## Controlling a running crawl

```java
// Start asynchronously
crawler.startAsync(config);

// Stop gracefully (waits for in-flight documents to finish)
crawler.stop();

// Check status
CrawlSessionStatus status = crawler.getSessionStatus();
```

## Next steps

- [Concepts: Extending the Crawler](../concepts/extending) — custom components and SPI
- [Configuration Reference ↗](https://crawlerconfig.norconex.com/docs) — full config options
