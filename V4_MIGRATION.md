V3 to V4
========

All notes we can take for users, to facilitate migration from V3 to V4.

Renamed
-------

### General

* All interfaces prefixed with "I" were renamed to drop the "I".

### Packages:

* `com.norconex.collector` → `com.norconex.crawler`
* `com.norconex.committer.core3` → `com.norconex.committer.core`

### Classes/methods:

* `Collector*` → `CrawlSession*`
* `CollectorEvent#COLLECTOR_*` → `CrawlSessionEvent#SESSION_*`
* `Collector#maxMemoryPool` → `CrawlSession#maxStreamCachePoolSize`
* `Collector#maxMemoryInstance` → `CrawlSession#maxStreamCacheSize`
* `CollectorLifeCycleListener#onCollector*`
  → `CrawlSessionLifeCycleListener#onCrawlSession*`

* `ImporterConfig#maxMemoryPool` → `ImporterConfig#maxStreamCachePoolSize`
* `ImporterConfig#maxMemoryInstance` → `ImporterConfig#maxStreamCacheSize`
* `ImporterConfig#DEFAULT_MAX_MEM_POOL` → `ImporterConfig#DEFAULT_MAX_STREAM_CACHE_POOL_SIZE`
* `ImporterConfig#DEFAULT_MAX_MEM_INSTANCE` → `ImporterConfig#DEFAULT_MAX_STREAM_CACHE_SIZE`

* `ImporterConfig#maxMemoryPool` → `ImporterConfig#maxStreamCachePoolSize`

Removed
-------

* Removed classes methods deprecated in previous major release.
* Removed "tempDir". Only "workDir" is configurable now. Classes 
  in need of a  temporary directory would derive it from the work 
  dir (or use the OS-defined temporary directory).
* Removed collection setters accepting both "vargars" and a collection
  to now only accept a collection. 
* Removed CrawlerConfigLoader.

XML Changes
-----------

* `collector` → `crawlSession`
* `maxMemoryPool` → `maxStreamCachePoolSize`
* `maxMemoryInstance` → `maxStreamCacheSize`


Misc. Changes
-------------

* Minimum Java version: 17
* Different features originally found in HTTP Collector and Filesystem Collector
  were moved to Crawler Core.
* Removed configuration flags to ignore/disabled a given feature when setting
  the corresponding object to null has the same effect.

### Committer Core

* MemoryCommitter#clean will now clear the cached requests.
* New CommitterService and CommitterServiceEvent classes.

### Importer

* Added Apache Velocity JSR 223 Script Engine.
* JavaScript JSR 223 Script Engine now using GraalVM implementation.
* The Operator inner class on DateMetadataFilter and NumericMetadataFilter
  were removed in favor of com.norconex.commons.lang.Operator
* Renamed DocInfo to DocRecord
* New SaveDocumentTagger class.
* CommonMatchers pattern constants are now Collections instead of arrays.
* Classes dealing with time zones now default to UTC when
  zone is not declared.

### Crawler Core

* Renamed CrawlDocInfo to CrawlDocRecord.
* Renamed .cmdline package to .cli
* Store export no longer prepare the store for a new crawl (exports as is).
* CrawlerCommitterService has been migrated from Crawler Core to
  Committer Core
* CrawlState renamed to CrawlDocState
* CrawlerLifeCycleListener is now abstract
* New CRAWLER_ERROR event.
* New crawler "idleTimeout" configuration option.
* New crawler "minProgressLoggingInterval" configuration option.
* MetadataFilter and ReferenceFilter renamed to GenericMetadataFilter and 
  GenericReferenceFilter.
* Renamed CollectorCommandLcuncher to CliLauncher.
* New MVStoreDataStoreConfig#ephemeral property for in-memory storage.
* The "maxDocuments" feature now represents the number of document processed
  within a crawling session. If the crawler did not reach completion, 
  the next session will resume where it last ended.
  
### Crawler Web

* `com.norconex.collector.http` → `com.norconex.crawler.web`
* `*.Http*` → `*.Web*`
* Removed Crawler configuration option "keepDownloads" and corresponding 
  CrawlerEvent.DOCUMENT_SAVED event in favor of new Importer SaveDocumentTagger.
* References to HttpCollector or Collector changed to WebCrawlSession 
  and CrawlSession, respectively.
* GenericRecrawlableResolver minimum frequencies now expect TextMatcher 
  instead of regular expressions.
* RobotsTxt now instantiated via builder factory method.
* SitemapChangeFrequency#getSitemapChangeFrequency renamed to #of.
* URLNormalizer was renamed WebURLNormalizer to distinguish from 
  com.norconex.commons.lang.url.URLNormalizer.
* GenericURLNormalizer enum constants are now uppercase.
* Moved fetchers to crawler-core: "httpFetchers" now just "fetchers".
* Moved startURLs* configuration options to crawler-core.

### Crawler File System

* Too many changes to list. Major refactor to bring up to speed with V4-stack 
  features.