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

### Committer Core

* MemoryCommitter#clean will now clear the cached requests.

### Importer

* Added Apache Velocity JSR 223 Script Engine.
* JavaScript JSR 223 Script Engine now using GraalVM implementation.
* The Operator inner class on DateMetadataFilter and NumericMetadataFilter
  were removed in favor of com.norconex.commons.lang.Operator
* Renamed DocInfo to DocRecord

### Committer Core
* Renamed CrawlDocInfo to CrawlDocRecord.
* Renamed .cmdline package to .cli
  
