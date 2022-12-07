# V3 to V4

All notes we can take for users, to facilitate migration from V3 to V4.

## Renamed

Packages:
* `com.norconex.collector` → `com.norconex.crawler`

Classes/methods:
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


## Removed

* Removed classes methods deprecated in previous major release.
* Removed "tempDir". Only "workDir" is configurable now. Classes 
  in need of a  temporary directory would derive it from the work 
  dir (or use the OS-defined temporary directory).
* Removed collection setters accepting both "vargars" and a collection
  to now only accept a collection. 
* Removed CrawlerConfigLoader.

## XML Changes

* `collector` → `crawlSession`
* `maxMemoryPool` → `maxStreamCachePoolSize`
* `maxMemoryInstance` → `maxStreamCacheSize`


## Misc Changes



