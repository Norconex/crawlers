---
title: Installation
---

# Installation

## Requirements

- **Java 21** or later (JRE is sufficient for CLI use)

Verify your Java version:

```bash
java -version
```

## Standard vs Full distributions

Each crawler ships in two variants:

- **Standard** — covers most use cases and has a smaller footprint.
- **Full** — includes everything in Standard plus optional extras: OCR, NLP language detection, extra parsers, scripting, and more.

Start with Standard unless you know you need those extras.

## Option 1 — Download the ZIP

1. Go to the [Download](/download) page and grab the latest release ZIP for
   your chosen crawler.

2. Extract it (the folder name matches the ZIP name):

   ```bash
   # Standard distribution
   unzip nx-crawler-web-4.0.0-standard.zip

   # Full distribution
   unzip nx-crawler-web-4.0.0-full.zip
   ```

3. The extracted folder looks like this:

   ```
   nx-crawler-web-4.0.0-standard/
     crawl-web.sh      ← Linux/macOS launch script
     crawl-web.bat     ← Windows launch script
     log4j2.xml        ← logging configuration
     examples/         ← ready-to-run sample configurations
     lib/              ← all JAR dependencies
     scripts/          ← utility scripts
     third-party/      ← third-party notices
     README.txt
     LICENSE.txt
   ```

4. On Linux/macOS, make the launch script executable:

   ```bash
   chmod +x crawl-web.sh
   ```

5. Verify the installation:

   ```bash
   # Linux/macOS
   ./crawl-web.sh --help

   # Windows
   .\crawl-web.bat --help
   ```

:::info[File System Crawler]
All examples above use the Web Crawler (`nx-crawler-web`, `crawl-web.sh`).
The File System Crawler is installed identically — substitute `web` with `fs` in every filename
and command (e.g. `nx-crawler-fs-4.0.0-standard.zip`, `crawl-fs.sh`).
:::

## Option 2 — Maven dependency

For embedding the crawler in a Java application, see [Java Integration](./java-integration).

## Option 3 — Build from source

```bash
git clone https://github.com/Norconex/crawlers.git
cd crawlers

# Web crawler
mvn clean package -pl crawler/web -Dmaven.test.skip=true

# File system crawler
mvn clean package -pl crawler/fs -Dmaven.test.skip=true
```

The distribution ZIP files will be in `crawler/web/target/` or `crawler/fs/target/`.

---

Next: [Web Quick Start](./quick-start-web) or [File System Quick Start](./quick-start-fs)
