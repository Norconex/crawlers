---
title: Crawl Sessions
---

# Crawl Sessions

A **crawl session** is a single named run of the crawler, backed by persistent on-disk state.
Sessions are what give Norconex Crawler its enterprise reliability characteristics:
you can stop, resume, and reschedule crawls without losing progress or recrawling everything.

## Session identity

Every crawler configuration has an `id` field.
This ID is used as the name of the session state directory under the configured `workDir`.

```yaml
crawlers:
  - id: acme-website # ← session identity
    workDir: /var/crawler/state
    startUrls:
      - https://www.example.com
```

Two configs with the same `id` and `workDir` share the same session state.
Changing the `id` or deleting the `workDir` starts a fresh crawl.

## Start, stop, and resume

| CLI command | Effect                                                       |
| ----------- | ------------------------------------------------------------ |
| `start`     | Begin or resume a session                                    |
| `stop`      | Gracefully stop — in-flight documents finish, state is saved |
| `clean`     | Delete session state — next `start` is a full recrawl        |

When you `stop` and then `start` again, the crawler picks up from where it left off:
unvisited references remain in the queue, already-committed documents are not re-committed.

## Deduplication

The crawler tracks every document it has processed in the session store.
On subsequent crawls, it detects whether a document has changed using:

- **Checksum-based** (default): compare a hash of the document's content or metadata
- **Modified date**: compare the `Last-Modified` HTTP header or filesystem timestamp
- **ETag**: use HTTP ETags for web resources

Unchanged documents are skipped. Only new or modified documents are committed.
Deleted documents (no longer reachable) can optionally trigger a delete event on the committer.

## Recrawl scheduling

By default, a session runs once and exits. For continuous monitoring, configure a recrawl delay:

```yaml
crawlers:
  - id: acme-website
    recrawlDelay:
      minDelay: PT4H # at least 4 hours between recrawls of the same page
```

Or use an external scheduler (cron, systemd timer, Kubernetes CronJob) to invoke `crawler-cli.sh start` on a schedule.
The session state ensures only changed content is processed on each run.

## State storage

Session state is stored in an embedded key-value store in the `workDir`.
For clustered deployments, the state backend can be replaced with a distributed store (e.g., Hazelcast, JDBC).
See the [Configuration Reference](https://crawlerconfig.norconex.com/docs) for storage backends.

## Multiple crawlers in one session

A single config file can contain multiple `crawlers` entries.
They run sequentially by default. Use `parallelCrawlers: true` to run them concurrently.

```yaml
crawlers:
  - id: website-a
    startUrls: [https://a.example.com]
  - id: website-b
    startUrls: [https://b.example.com]
```
