# Benchmark Workspace

This top-level `benchmark` folder is shared across modules and is intended for repeatable performance benchmarking.

## Why top-level?

- Reusable by `crawler/web` now and by other modules later.
- Keeps benchmark code/data out of production module artifacts.
- Easier to run full benchmark suites from one place.

## Structure

- `harness/` — benchmark orchestration and report generation.
- `web-fixtures/` — local synthetic web server for deterministic test sites.
- `scenarios/web/` — benchmark scenario definitions.
- `results/` — local benchmark outputs (JSON/CSV/Markdown reports).

## Initial workflow (target state)

1. Start fixture server from `web-fixtures/`.
2. Run scenario(s) from `scenarios/web/` using harness.
3. Write reports to `results/<timestamp>/`.
4. Compare against a baseline run.

Tip: when using `run-benchmark.ps1` with `-StartFixture`, pass
`-NoFixtureCleanup` to keep the fixture server running for manual debugging.
