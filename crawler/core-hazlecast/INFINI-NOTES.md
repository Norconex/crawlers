```
+------------------------+        +------------------------+
| CrawlerCoordinator     |<-------| ClusterManager         |
+------------------------+        +------------------------+
| - coordinateWork()     |        | - discoverNodes()      |
| - assignTasks()        |        | - monitorHealth()      |
+------------------------+        +------------------------+
          ^                               |
          |                               |
          | manages                       | manages
          |                               v
+------------------------+        +------------------------+
| WorkDistributor        |        | CrawlerNode (interface)|
+------------------------+        +------------------------+
| - distributeURLs()     |<-------| - crawl(URL)           |
| - trackProgress()      |        | - getDiscoveredURLs()  |
+------------------------+        +------------------------+
          ^                         ^             ^
          |                         |             |
          | uses                    |             |
          |                         |             |
+------------------------+  +----------------+ +----------------+
| InfinispanCache        |  | LocalNode      | | RemoteNode     |
+------------------------+  +----------------+ +----------------+
| - visitedURLsCache     |  | - Thread-based | | - RMI/REST     |
| - workQueue            |  |   crawling     | |   based        |
| - discoveredURLsCache  |  +----------------+ +----------------+
+------------------------+
          ^
          |
          | used by
          |
+------------------------+
| URLProcessor           |
+------------------------+
| - extractLinks()       |
| - storeContent()       |
| - checkRobotsTxt()     |
+------------------------+

```


```java
package com.example.crawler;

import org.infinispan.Cache;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DistributedCrawler {
    private final EmbeddedCacheManager cacheManager;
    private final Cache<String, CrawlStatus> urlStatusCache;
    private final Cache<String, CrawlData> contentCache;
    private final URLFrontier urlFrontier;
    private final int threadCount;

    public DistributedCrawler(String configFile, int threadCount) throws Exception {
        this.threadCount = threadCount;
        // Initialize Infinispan with configuration
        this.cacheManager = new DefaultCacheManager(configFile);
        this.urlStatusCache = cacheManager.getCache("urlStatus");
        this.contentCache = cacheManager.getCache("content");
        this.urlFrontier = new URLFrontier(cacheManager.getCache("urlFrontier"));
    }

    public void start(String seedUrl) {
        // Add seed URL to frontier
        urlFrontier.addUrl(seedUrl);
        
        // Create worker threads
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(new CrawlerNode(urlStatusCache, contentCache, urlFrontier));
        }
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdown();
            try {
                executor.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            cacheManager.stop();
        }));
    }

    public static void main(String[] args) throws Exception {
        String seedUrl = args.length > 0 ? args[0] : "https://example.com";
        int threadCount = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        
        DistributedCrawler crawler = new DistributedCrawler("infinispan.xml", threadCount);
        crawler.start(seedUrl);
    }
}
```


```java
package com.example.crawler;

import org.infinispan.Cache;

import java.io.IOException;
import java.net.URL;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CrawlerNode implements Runnable {
    private final Cache<String, CrawlStatus> urlStatusCache;
    private final Cache<String, CrawlData> contentCache;
    private final URLFrontier urlFrontier;
    private final String nodeId;

    public CrawlerNode(Cache<String, CrawlStatus> urlStatusCache, 
                       Cache<String, CrawlData> contentCache,
                       URLFrontier urlFrontier) {
        this.urlStatusCache = urlStatusCache;
        this.contentCache = contentCache;
        this.urlFrontier = urlFrontier;
        this.nodeId = generateNodeId();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String url = urlFrontier.getNextUrl();
                if (url == null) {
                    TimeUnit.SECONDS.sleep(1);
                    continue;
                }

                if (urlStatusCache.putIfAbsent(url, CrawlStatus.IN_PROGRESS) != null) {
                    continue; // URL is being processed by another node
                }

                try {
                    CrawlData data = crawlUrl(url);
                    contentCache.put(url, data);
                    urlStatusCache.put(url, CrawlStatus.COMPLETED);
                    
                    // Extract and add new URLs to frontier
                    Set<String> links = CrawlerUtils.extractLinks(data.getContent(), url);
                    for (String link : links) {
                        if (CrawlerUtils.shouldCrawl(link) && 
                            urlStatusCache.getOrDefault(link, null) == null) {
                            urlFrontier.addUrl(link);
                        }
                    }
                } catch (Exception e) {
                    urlStatusCache.put(url, CrawlStatus.ERROR);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private CrawlData crawlUrl(String url) throws IOException {
        // Implement actual crawling logic here
        String content = CrawlerUtils.downloadUrl(url);
        return new CrawlData(url, content, System.currentTimeMillis(), nodeId);
    }

    private String generateNodeId() {
        return "node-" + System.currentTimeMillis() + "-" + 
               Math.abs(System.nanoTime() % 10000);
    }
}

```

```java
package com.example.crawler;

import java.io.Serializable;

public class CrawlData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String url;
    private final String content;
    private final long timestamp;
    private final String crawledBy;
    
    public CrawlData(String url, String content, long timestamp, String crawledBy) {
        this.url = url;
        this.content = content;
        this.timestamp = timestamp;
        this.crawledBy = crawledBy;
    }
    
    // Getters
    public String getUrl() { return url; }
    public String getContent() { return content; }
    public long getTimestamp() { return timestamp; }
    public String getCrawledBy() { return crawledBy; }
}
```

```java
package com.example.crawler;

import java.io.Serializable;

public enum CrawlStatus implements Serializable {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ERROR
}
```


```java
package com.example.crawler;

import org.infinispan.Cache;
import java.util.concurrent.atomic.AtomicLong;

public class URLFrontier {
    private final Cache<Long, String> urlQueue;
    private final AtomicLong headIndex = new AtomicLong(0);
    private final AtomicLong tailIndex = new AtomicLong(0);
    
    public URLFrontier(Cache<Long, String> urlQueue) {
        this.urlQueue = urlQueue;
        // Initialize indices from cache if available
        initializeIndices();
    }
    
    public void addUrl(String url) {
        urlQueue.put(tailIndex.getAndIncrement(), url);
    }
    
    public String getNextUrl() {
        long current = headIndex.get();
        if (current >= tailIndex.get()) {
            return null;
        }
        
        String url = urlQueue.get(current);
        if (url != null && headIndex.compareAndSet(current, current + 1)) {
            urlQueue.remove(current);
            return url;
        }
        
        return null;
    }
    
    private void initializeIndices() {
        // Find max key in cache to restore queue state
        urlQueue.keySet().stream().max(Long::compare).ifPresent(max -> {
            headIndex.set(urlQueue.keySet().stream().min(Long::compare).orElse(0L));
            tailIndex.set(max + 1);
        });
    }
}

```


```java
<?xml version="1.0" encoding="UTF-8"?>
<infinispan xmlns="urn:infinispan:config:13.0">
   <cache-container default-cache="urlStatus">
      <transport cluster="crawler-cluster" stack="tcp"/>
      
      <local-cache name="urlStatus">
         <persistence>
            <file-store path="crawler-data/status" max-entries="1000000"/>
         </persistence>
      </local-cache>
      
      <local-cache name="content">
         <memory>
            <off-heap size="100MB" eviction="MEMORY" strategy="REMOVE"/>
         </memory>
         <persistence>
            <file-store path="crawler-data/content" max-entries="10000" 
                        passivation="true" preload="false"/>
         </persistence>
      </local-cache>
      
      <local-cache name="urlFrontier">
         <persistence>
            <file-store path="crawler-data/frontier" max-entries="1000000"/>
         </persistence>
      </local-cache>
   </cache-container>
</infinispan>
```


```java
package com.example.crawler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CrawlerUtils {
    private static final Pattern LINK_PATTERN = 
        Pattern.compile("href=[\"']([^\"'#]+)[\"']", Pattern.CASE_INSENSITIVE);
    
    public static String downloadUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        StringBuilder content = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(url.openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        
        return content.toString();
    }
    
    public static Set<String> extractLinks(String content, String baseUrl) {
        Set<String> links = new HashSet<>();
        Matcher matcher = LINK_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String link = matcher.group(1);
            link = normalizeUrl(link, baseUrl);
            if (link != null) {
                links.add(link);
            }
        }
        
        return links;
    }
    
    public static String normalizeUrl(String url, String baseUrl) {
        try {
            if (url.startsWith("http")) {
                return new URL(url).toString();
            } else {
                return new URL(new URL(baseUrl), url).toString();
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    public static boolean shouldCrawl(String url) {
        // Add custom filtering logic here
        return url != null && url.startsWith("http");
    }
}

```



    1. For the crawling units (CrawlerNode interface):
    2. CrawlerWorker
    3. CrawlerAgent
    4. CrawlProcessor
    5. For local thread-based implementations (LocalNode):
    6. ThreadWorker
    7. LocalExecutor
    8. InProcessCrawler
    9. For remote implementations (RemoteNode):
    10. RemoteAgent
    11. ServiceCrawler
    12. DistributedWorker
    13. For physical machines/JVMs in the cluster:
    14. Instance
    15. Server
    16. Member
    17. Host
    18. For the coordinator (previously ClusterManager):
    19. ClusterOrchestrator
    20. ControlPlane
    21. CrawlDirector
    
    
    
Based on the discussion of our distributed crawler system, here are the abstraction layers by granularity (from finest to coarsest):

Execution Layers

    1. Thread Level (finest)
    2. Individual execution units within a process
    3. ThreadWorkers handling single crawl operations
    4. Process/JVM Level
    5. Single JVM instances containing multiple threads
    6. InProcessCrawlers managing local execution
    7. Service Level
    8. Components with specific responsibilities
    9. URLProcessor, WorkDistributor, etc.
    10. Host Level
    11. Physical/virtual machines running multiple services
    12. Servers or Instances in the cluster
    13. Cluster Level (coarsest)
    14. Collection of hosts working together
    15. Managed by the CrawlDirector

Data Layers

    1. URL Level (finest)
    2. Individual web resources being processed
    3. Batch Level
    4. Groups of URLs assigned to workers
    5. Distributed Cache Level
    6. Shared data structures across the cluster
    7. Infinispan caches for coordination
    8. Global Crawl State (coarsest)
    9. Complete system state spanning all machines

These layers provide a hierarchical view of both execution units and data organization within the distributed crawler system.
