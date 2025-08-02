I’ve sketched a flexible, modular monorepo blueprint that:

    Implements the four core pillars—frontier design, scheduler loop, discovery, dedupe & politeness—each in its own module.

    Leverages Norconex HTTP Collector for fetching and politeness out of the box.

    Supports multi‑tenant namespaces, priority scheduling, finite & continuous modes, and “code-once” portability via Apache Beam (plus optional NiFi/Camel integration).

    Outsources committers to Kafka Connect or NiFi/Camel where you prefer configuration over custom code.

    Includes unit and integration test strategies (Beam’s TestStream, embedded Norconex, WireMock, mini-clusters).

The provided skeleton outlines module boundaries, key classes/interfaces, and a sample directory layout. You can now fill in each module with your preferred frameworks—Beam pipelines, Norconex adapters, NiFi flows or Camel routes—and assemble them under one synchronized versioned build. Let me know if you’d like deeper code examples in any specific module!


---------------------


## Distributed Crawler High-Level Architecture

This solution unifies everything we've discussed into a **"code-once, run-anywhere"** monorepo, with modular layers and optional integrations (Beam, NiFi, Camel, Kafka Connect). It supports both **finite** and **continuous** modes, and can be deployed from IDE-local to multi-node clusters.

---

### 1. Frontier Design

* **Module**: `crawler-core-frontier`
* **Responsibilities**:

  * URL buffer abstraction (plug-in for in-memory, Kafka/Pulsar, Infinispan)
  * Pluggable **Queue** interface: `claimBatch(batchSize)`, `enqueueAll(urls)`
  * Optional **Priority** and **Multi‑tenant** namespace support (keyed by tenantId)
* **Implementations**:

  * `InMemoryQueue` (DirectRunner / local dev)
  * `KafkaQueue` (via `KafkaIO` topics)
  * `NorconexQueueAdapter` (wrap Norconex frontier)

### 2. Scheduler Loop

* **Module**: `crawler-scheduler`
* **Responsibilities**:

  * **Finite mode**: seed once from config, sitemap, seed list
  * **Continuous mode**: periodic scans (`PeriodicImpulse`), sitemap/RSS watchers, webhook listeners
  * Assign `priority` or `nextFetchTs` per URL using change hints
  * Enqueue to frontier (with tenant/context)
* **Key Components**:

  * `SeedLoader` (static seeds)
  * `DeltaScheduler` (periodic scan of DB for due URLs)
  * `FeedListener` (RSS, webhooks)

### 3. Discovery

* **Module**: `crawler-http` (Norconex integration)
* **Responsibilities**:

  * Fetch with `If-Modified-Since` / `ETag`
  * Parse HTML, extract links
  * Normalize & canonicalize URLs
  * Wrap Norconex HTTP Collector’s `HttpClientManager` + politeness controller
* **Outputs**:

  * `FetchResult { url, status, content, discoveredUrls }`
  * 304: skip content, update metadata
  * 404/410: emit deletion event

### 4. Deduplication & Politeness

* **Module**: `crawler-politeness`
* **Responsibilities**:

  * **BloomFilter** per tenant (in-memory or Redis) for global dedupe
  * **Per‑host politeness** via Beam state & timers or Norconex controller
  * Retry/backoff management
* **Implementations**:

  * `BeamPolitenessDoFn`
  * `NorconexPolitenessController`

---

## Optional Integration Layers

* **Apache Beam**: `crawler-beam-pipeline`

  * "1 pipeline" covering seed → frontier → politeness → fetch → extract → re-enqueue → commit
  * DirectRunner / FlinkRunner / SparkRunner
* **Apache NiFi**: `crawler-nifi-flow`

  * Use `PublishKafka`, `ConsumeKafka`, `InvokeHTTP`, `RouteOnAttribute` processors
* **Apache Camel**: `crawler-camel-routes`

  * Define `from("kafka://frontier").to("direct:fetch")…` routes
* **Kafka Connect**: offload committers to Connect sinks (Elasticsearch, JDBC, Solr)

---

## Finite vs. Continuous Modes

* **Finite**: start-only seeds, bounded Beam sources, job terminates when frontier empty
* **Continuous**: unbounded sources (`PeriodicImpulse`, feeds, webhooks), streaming pipeline never ends

---

## Unit Testing Strategy

* **Crawler-core**: mock `Queue` interface, verify claim/enqueue logic
* **Scheduler**: use Beam TestStreams / NiFi test harness
* **Fetch**: wire Norconex in local mode, stub HTTP servers with WireMock
* **Politeness**: Beam state/timer emulator, Norconex unit tests
* **End-to-end**: integration tests via MiniCluster (Beam), Embedded NiFi, Embedded Kafka

---

## Monorepo Skeleton Project Structure

```
crawler-monorepo/
├── crawler-core-frontier/
│   ├── src/main/java/... Queue.java QueueFactory.java
│   └── src/test/java/... FrontierTest.java
├── crawler-scheduler/
│   ├── src/main/java/... SchedulerPipeline.java
│   └── src/test/java/... SchedulerTest.java
├── crawler-http-norconex/
│   ├── src/main/java/... NorconexFetcher.java
│   └── src/test/java/... FetcherTest.java
├── crawler-politeness/
│   ├── src/main/java/... BloomFilterDedupe.java
│   └── src/test/java/... PolitenessTest.java
├── crawler-beam-pipeline/
│   ├── src/main/java/... MainPipeline.java
│   └── src/test/java/... PipelineTest.java
├── crawler-nifi-flow/      # NiFi templates & config
├── crawler-camel-routes/   # Camel route definitions & config
├── connectors-kafka-connect/ # Connector configs
├── examples/
│   ├── local-dev.sh      # Scripts to launch DirectRunner + embedded queue
│   └── deploy-k8s.yaml  # Kubernetes manifests for Beam, Kafka, ZK, etc.
└── build.gradle (or pom.xml)
```

---

This blueprint ensures:

* **Modularity**: swap queue, scheduler, fetcher, politeness implementations
* **Reusability**: Norconex HTTP Collector powers fetch + politeness
* **Portability**: single codebase runs in IDE, Docker, Kubernetes
* **Extensibility**: hook in NiFi/Camel or Kafka Connect for special needs
* **Testability**: unit & integration tests for each module
