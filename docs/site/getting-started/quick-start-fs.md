---
title: Filesystem Crawler Quick Start
---

# Filesystem Crawler Quick Start

This guide gets you from zero to a running filesystem crawl in under 5 minutes.

## Step 1 — Create a config file

Create `my-fs-crawl.yaml`:

```yaml
crawlerDefaults:
  numThreads: 5
  committers:
    - class: com.norconex.committer.core.impl.LogCommitter

crawlers:
  - id: my-fs-crawl
    startPaths:
      - /path/to/your/documents
```

The `LogCommitter` prints each processed document to the log — ideal for testing before connecting a real backend.

## Step 2 — Filter by file type (optional)

To crawl only specific file extensions:

```yaml
crawlers:
  - id: my-fs-crawl
    startPaths:
      - /path/to/your/documents
    pathsFilter:
      includes:
        - ".*\\.(pdf|docx|xlsx|pptx|txt|html)$"
```

## Step 3 — Start the crawl

```bash
crawler-cli.sh start -config my-fs-crawl.yaml
```

## Step 4 — Remote filesystems

**SFTP:**

```yaml
crawlers:
  - id: sftp-crawl
    startPaths:
      - sftp://user@fileserver.example.com/data/documents
    credentials:
      username: user
      passwordFile: /etc/crawler/sftp.password
```

**WebDAV (SharePoint, Nextcloud):**

```yaml
crawlers:
  - id: sharepoint-crawl
    startPaths:
      - https://sharepoint.example.com/sites/mysite/Shared Documents/
    credentials:
      username: domain\\user
      passwordFile: /etc/crawler/sp.password
```

**Apache HDFS:**

```yaml
crawlers:
  - id: hdfs-crawl
    startPaths:
      - hdfs://namenode:9000/user/data/corpus
```

## Step 5 — Send to a search engine

Replace the `LogCommitter` with your target. See the [Integrations](/integrations) page for all options.

## CLI reference

| Command                | Description                                |
| ---------------------- | ------------------------------------------ |
| `start -config <file>` | Start or resume a crawl                    |
| `stop -config <file>`  | Gracefully stop a running crawl            |
| `clean -config <file>` | Delete crawl state (forces a full recrawl) |
| `info -config <file>`  | Print crawl status                         |

## Next steps

- Use the [Configuration Editor](https://crawlerconfig.norconex.com) to build your config visually
- Read [Concepts: Crawl Pipeline](../concepts/crawl-pipeline) to understand how documents flow
- See [Concepts: Sessions](../concepts/sessions) for resume and scheduling behavior
