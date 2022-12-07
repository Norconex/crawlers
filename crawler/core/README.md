crawler-core
==============

Collector-related code shared between different collector implementations

Website: https://opensource.norconex.com/crawlers/core/




CrawlSessionConfig(CrawlerConfig.class)
  - private Class crawlerConfigClass = CrawlerConfig.class







CrawlSession(CrawlerImplBuilder crawlerBuider, crawlSessionConfig, eventManager)


CrawlSessionBuilder
   crawlerFactory (which would use Crawler.builder())
   crawlSessionConfig
   eventManager
   
The crawlSessionBuilder will be abstracted by each imlpementation (web, fs, etc.)   Example: WebCrawler.get(config, eventManager);



V4:
------

BOTH CrawlSession, CrawlSessionConfig and Crawler are the same for all implementations (web, filesystem, etc).

Only CrawlerConfig and CrawlerBuilder (inner workings) are different per implementation
