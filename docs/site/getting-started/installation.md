---
title: Installation
---

# Installation

## Requirements

- **Java 21** or later (JRE is sufficient for CLI use)
- No other runtime dependencies — the distribution ZIP is self-contained

Verify your Java version:

```bash
java -version
```

## Option 1 — Download the ZIP distribution

1. Go to the [Download](/download) page and grab the latest release ZIP.
2. Extract it:

```bash
unzip norconex-crawler-4.x.x-dist.zip -d ~/norconex-crawler
```

3. The extracted folder contains:

```
norconex-crawler/
  bin/
    crawler-cli.sh        # Linux / macOS
    crawler-cli.bat       # Windows
  lib/                    # all JARs
  examples/               # ready-to-run example configs
```

4. Make the CLI executable (Linux/macOS):

```bash
chmod +x ~/norconex-crawler/bin/crawler-cli.sh
```

5. Verify:

```bash
~/norconex-crawler/bin/crawler-cli.sh --help
```

## Option 2 — Maven dependency

For embedding the crawler in a Java application, see [Java Integration](./java-integration).

## Option 3 — Build from source

```bash
git clone https://github.com/Norconex/crawlers.git
cd crawlers
mvn clean package -pl crawler/web -Dmaven.test.skip=true
```

The distribution ZIP will be in `crawler/web/target/`.

---

Next: [Web Quick Start](./quick-start-web) or [Filesystem Quick Start](./quick-start-fs)
