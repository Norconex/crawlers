package com.norconex.crawler.benchmark.harness;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sun.management.OperatingSystemMXBean;

public final class BenchmarkHarnessMain {

    private static final Pattern HREF_PATTERN = Pattern.compile(
            "href\\s*=\\s*([\"'])(.*?)\\1",
            Pattern.CASE_INSENSITIVE);

    private BenchmarkHarnessMain() {
    }

    public static void main(String[] args) throws Exception {
        var options = parseArgs(args);
        var scenarioPath = Path.of(required(options, "--scenario"));
        var outDir = Path.of(options.getOrDefault("--out",
                "benchmark/results/" + Instant.now().toString()
                        .replace(':', '-')));
        var fixtureBase = options.getOrDefault("--fixtureBase",
                "http://localhost:8181");
        var baselineDir = options.get("--baseline");

        var scenario = loadScenario(scenarioPath);
        Files.createDirectories(outDir);

        var run = executeScenario(scenario, fixtureBase, outDir);
        var summaryPath = outDir.resolve("summary.json");
        var metricsPath = outDir.resolve("metrics.csv");
        var reportPath = outDir.resolve("report.md");

        writeSummary(summaryPath, run.summary);
        writeMetrics(metricsPath, run.metricsRows);
        writeReport(reportPath, run.summary, baselineDir);

        System.out.println("Benchmark completed.");
        System.out.println("  Scenario: " + scenarioPath);
        System.out.println("  Output  : " + outDir.toAbsolutePath());
        System.out.println("  Summary : " + summaryPath.toAbsolutePath());
        System.out.println("  Metrics : " + metricsPath.toAbsolutePath());
        System.out.println("  Report  : " + reportPath.toAbsolutePath());
    }

    private static RunResult executeScenario(Map<String, Object> scenario,
            String fixtureBase,
            Path outDir) throws Exception {
        var summary = new LinkedHashMap<String, Object>();
        var metricsRows = new ArrayList<String>();
        metricsRows.add(
                "timestamp_ms,elapsed_ms,visited_count,queue_size,heap_used_bytes,process_cpu_load");

        var name = asString(scenario.get("name"), "unknown");
        var seed = asLong(scenario.get("seed"), 0L);

        @SuppressWarnings("unchecked")
        var fixture = (Map<String, Object>) scenario
                .getOrDefault("fixture", Map.of());
        @SuppressWarnings("unchecked")
        var crawler = (Map<String, Object>) scenario
                .getOrDefault("crawler", Map.of());
        @SuppressWarnings("unchecked")
        var metricsCfg = (Map<String, Object>) scenario
                .getOrDefault("metrics", Map.of());

        var rootPath = asString(fixture.get("rootPath"),
                "/site/default/seed/42/root");
        var targetDocs = Math.max(1, asInt(crawler.get("targetDocs"), 1000));
        var maxDuration = parseDuration(asString(crawler.get("maxDuration"),
                "PT10M"));
        var sampleIntervalMs = Math.max(250,
                asInt(metricsCfg.get("sampleIntervalMs"), 1000));

        var rootUri = URI.create(trimTrailingSlash(fixtureBase) + rootPath);

        var processBean = (OperatingSystemMXBean) ManagementFactory
                .getOperatingSystemMXBean();
        long startMs = System.currentTimeMillis();
        Instant start = Instant.now();
        long deadlineMs = startMs + maxDuration.toMillis();

        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        Set<URI> visited = new LinkedHashSet<>();
        Deque<URI> queue = new ArrayDeque<>();
        queue.add(rootUri);

        var counters = new Counters();

        var sampler = Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long elapsed = now - startMs;
            long heapUsed = Runtime.getRuntime().totalMemory()
                    - Runtime.getRuntime().freeMemory();
            double cpuLoad = safeCpuLoad(processBean);
            metricsRows.add(now + "," + elapsed + "," + visited.size() + ","
                    + queue.size() + "," + heapUsed + ","
                    + String.format(Locale.ROOT, "%.5f", cpuLoad));
        }, sampleIntervalMs, sampleIntervalMs, TimeUnit.MILLISECONDS);

        try {
            while (!queue.isEmpty()
                    && visited.size() < targetDocs
                    && System.currentTimeMillis() < deadlineMs) {
                URI uri = queue.pollFirst();
                if (uri == null || visited.contains(uri)) {
                    continue;
                }
                visited.add(uri);

                try {
                    var req = HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(15))
                            .GET()
                            .build();
                    var res = client.send(req,
                            HttpResponse.BodyHandlers.ofByteArray());
                    counters.requests++;
                    int status = res.statusCode();
                    if (status >= 400) {
                        counters.errors++;
                    }

                    byte[] body = res.body();
                    counters.bytes += body.length;

                    Optional<String> contentType = res.headers()
                            .firstValue("content-type");
                    if (contentType.isPresent()
                            && contentType.get().toLowerCase(Locale.ROOT)
                                    .contains("text/html")) {
                        var html = new String(body, StandardCharsets.UTF_8);
                        extractLinks(uri, html).stream()
                                .filter(link -> sameHost(rootUri, link))
                                .filter(link -> !visited.contains(link))
                                .forEach(queue::addLast);
                    }
                } catch (Exception e) {
                    counters.errors++;
                }
            }
        } finally {
            sampler.shutdownNow();
        }

        Instant end = Instant.now();
        long durationMs = Duration.between(start, end).toMillis();
        double docsPerSecond = durationMs > 0
                ? (visited.size() * 1000d) / durationMs
                : 0d;

        summary.put("scenario", name);
        summary.put("seed", seed);
        summary.put("fixtureBase", fixtureBase);
        summary.put("rootUri", rootUri.toString());
        summary.put("targetDocs", targetDocs);
        summary.put("visitedDocs", visited.size());
        summary.put("requests", counters.requests);
        summary.put("errors", counters.errors);
        summary.put("bytes", counters.bytes);
        summary.put("startedAt", start.toString());
        summary.put("endedAt", end.toString());
        summary.put("durationMs", durationMs);
        summary.put("docsPerSecond", docsPerSecond);
        summary.put("outputDir", outDir.toAbsolutePath().toString());

        return new RunResult(summary, metricsRows);
    }

    private static List<URI> extractLinks(URI base, String html) {
        var links = new ArrayList<URI>();
        Matcher matcher = HREF_PATTERN.matcher(html);
        while (matcher.find()) {
            String href = matcher.group(2);
            if (href == null || href.isBlank()
                    || href.startsWith("#")
                    || href.startsWith("javascript:")) {
                continue;
            }
            try {
                links.add(base.resolve(href));
            } catch (IllegalArgumentException e) {
                // ignore invalid URL
            }
        }
        return links;
    }

    private static boolean sameHost(URI root, URI other) {
        return Objects.equals(root.getHost(), other.getHost())
                && normalizePort(root) == normalizePort(other);
    }

    private static int normalizePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static Map<String, Object> loadScenario(Path file)
            throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(file.toFile(),
                new TypeReference<Map<String, Object>>() {});
    }

    private static void writeSummary(Path path, Map<String, Object> summary)
            throws IOException {
        var mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(path.toFile(), summary);
    }

    private static void writeMetrics(Path path, List<String> rows)
            throws IOException {
        Files.write(path, rows, StandardCharsets.UTF_8);
    }

    private static void writeReport(Path path, Map<String, Object> summary,
            String baselineDir) throws IOException {
        var lines = new ArrayList<String>();
        lines.add("# Benchmark Report");
        lines.add("");
        lines.add("- Scenario: " + summary.get("scenario"));
        lines.add("- Seed: " + summary.get("seed"));
        lines.add("- Root URI: " + summary.get("rootUri"));
        lines.add("- Duration (ms): " + summary.get("durationMs"));
        lines.add("- Visited docs: " + summary.get("visitedDocs"));
        lines.add("- Requests: " + summary.get("requests"));
        lines.add("- Errors: " + summary.get("errors"));
        lines.add("- Bytes fetched: " + summary.get("bytes"));
        lines.add("- Docs/sec: " + summary.get("docsPerSecond"));
        lines.add("");
        if (baselineDir != null && !baselineDir.isBlank()) {
            lines.add("Baseline directory: " + baselineDir);
            lines.add(
                    "(Baseline comparison hook ready; add parser logic here.)");
            lines.add("");
        }
        lines.add("Artifacts:");
        lines.add("- summary.json");
        lines.add("- metrics.csv");
        lines.add("- report.md");
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseArgs(String[] args) {
        var options = new HashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                continue;
            }
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                options.put(key, args[i + 1]);
                i++;
            } else {
                options.put(key, "true");
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required argument " + key);
        }
        return value;
    }

    private static String asString(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static int asInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long asLong(Object value, long fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Duration parseDuration(String value) {
        try {
            return Duration.parse(value);
        } catch (Exception e) {
            return Duration.ofMinutes(10);
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static double safeCpuLoad(OperatingSystemMXBean bean) {
        if (bean == null) {
            return -1d;
        }
        try {
            return bean.getProcessCpuLoad();
        } catch (Exception e) {
            return -1d;
        }
    }

    private static final class Counters {
        private long requests;
        private long errors;
        private long bytes;
    }

    private record RunResult(Map<String, Object> summary,
            List<String> metricsRows) {
    }
}
