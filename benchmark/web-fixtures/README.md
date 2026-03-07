# Web Fixture Server

Synthetic website generator for reproducible crawler benchmarks.

## Run

From repository root:

```bash
mvn -f benchmark/web-fixtures/pom.xml -DskipTests compile
mvn -f benchmark/web-fixtures/pom.xml exec:java -Dexec.args="--port 8181"
```

Quick health check:

```bash
curl http://localhost:8181/health
```

## Goals

- Deterministic generation based on a random seed.
- Tunable topology and content mix.
- Exercise canonicalization, deduplication, and failure handling.

## Minimum capabilities

- Link graph controls: depth, branching, cycles.
- URL variants: query noise, non-canonical equivalents, redirect chains.
- Content mix: HTML, PDF, binary blobs, errors, delayed responses.
- Duplicate and near-duplicate content ratios.

## Implemented endpoints

- `GET /site/{scenario}/seed/{seed}/root`
- `GET /site/{scenario}/seed/{seed}/page/{depth}/{node}`
- `GET /asset/{scenario}/seed/{seed}/{kind}/{id}` (`kind`: `pdf` or any other value for binary)
- `GET /robots.txt`
- `GET /sitemap.xml`
- `GET /health`

Keep generation deterministic for identical scenario + seed.

## Query parameters for `/site/...`

- `depth` (default `4`)
- `branch` (default `8`)
- `avgSize` in bytes (default `32768`)
- `dupPct` (default `0.1`)
- `nonCanonicalPct` (default `0.05`)
- `pdfLinkPct` (default `0.08`)

Example root page:

```text
http://localhost:8181/site/mixed-media/seed/7/root?depth=5&branch=10&avgSize=65536&dupPct=0.25
```
