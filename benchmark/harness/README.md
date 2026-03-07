# Benchmark Harness

The harness runs scenarios, collects metrics, and writes reports.

## Run

From repository root:

```bash
mvn -f benchmark/harness/pom.xml exec:java \
	-Dexec.args="--scenario benchmark/scenarios/web/small-clean.yaml --out benchmark/results/run-01 --fixtureBase http://localhost:8181"
```

Or use the root helper script:

```powershell
Set-Location benchmark
./run-benchmark.ps1 -Scenario ./scenarios/web/small-clean.yaml -OutDir ./results/run-01
```

Keep fixture running after benchmark (debug mode):

```powershell
./run-benchmark.ps1 -Scenario ./scenarios/web/small-clean.yaml -OutDir ./results/run-01 -StartFixture -NoFixtureCleanup
```

## Scope

- Run one or many scenario files.
- Capture timings and throughput.
- Capture process metrics (CPU, heap, GC) where possible.
- Emit machine-readable and human-readable reports.

## Recommended outputs

- `summary.json`
- `metrics.csv`
- `report.md`

Current implementation emits all three files per run.

## Implemented command contract

```text
--scenario <file> --out <results-dir> [--fixtureBase <url>] [--baseline <results-dir>]
```

Notes:

- Reads one YAML scenario file per run.
- Crawls the synthetic fixture site from `fixture.rootPath`.
- Captures periodic process metrics in `metrics.csv`.
