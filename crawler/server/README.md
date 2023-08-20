Crawler Server
==============

## ⚠️ Unstable: Experimental️ Project ⚠️

**This is an "under development" playground for a new crawling component 
that may change a lot and may never be released (or can even be removed).
Do not rely on this project for anything significant just yet.**

--------------

Experiments towards an opt-in service/server/REST API layer on top of crawlers.

A few objectives (may change):

* Totally optional.
* Provide some admin capabilities.
* Allow to make ad-hoc crawl requests of various natures.
  * No "session" required.
  * No persistence required.
* Offer realtime insights.
  * Crawl progress, including counts
  * Maybe: cluster overview
* Works with both FS and Web crawlers.
* Offer REST API endpoints (OpenAPI).
  * Leverages existing schemas for auto-generating OpenAPI documentation/endpoints.
* Allow to launch crawling sessions.
  * Ability to launch on supported cloud provider clusters (Kafka Connect)
  * Maybe: ability to launch a standalone local instance (if only for development)?
  * Maybe: ability to launch a standalone remote instance (same as local, but on cloud provider)?



