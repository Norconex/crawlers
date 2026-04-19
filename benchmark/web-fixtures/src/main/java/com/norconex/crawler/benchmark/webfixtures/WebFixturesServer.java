/* Copyright 2026 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.norconex.crawler.benchmark.webfixtures;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal deterministic synthetic web fixture server.
 */
public final class WebFixturesServer {

    private static final int DEFAULT_PORT = 8181;

    private WebFixturesServer() {
    }

    public static void main(String[] args) throws Exception {
        int port = parsePort(args);
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/robots.txt", new RobotsHandler());
        server.createContext("/sitemap.xml", new SitemapHandler(port));
        server.createContext("/site", new SiteHandler(port));
        server.createContext("/asset", new AssetHandler());
        server.setExecutor(Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors())));
        server.start();

        System.out.printf("Web fixture server running at http://localhost:%d%n",
                port);
        System.out.println("Endpoints:");
        System.out.println("  /health");
        System.out.println("  /site/{scenario}/seed/{seed}/root");
        System.out
                .println("  /site/{scenario}/seed/{seed}/page/{depth}/{node}");
        System.out.println("  /asset/{scenario}/seed/{seed}/{kind}/{id}");
    }

    private static int parsePort(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_PORT;
        }
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return DEFAULT_PORT;
    }

    private static final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            writeText(exchange, 200, "text/plain",
                    "OK " + Instant.now().toString());
        }
    }

    private static final class RobotsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            writeText(exchange, 200, "text/plain", "User-agent: *\nAllow: /\n");
        }
    }

    private static final class SitemapHandler implements HttpHandler {
        private final int port;

        private SitemapHandler(int port) {
            this.port = port;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String base = "http://localhost:" + port;
            String xml = """
                    <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                    <urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">
                      <url><loc>%s/site/default/seed/42/root</loc></url>
                      <url><loc>%s/site/mixed-media/seed/7/root</loc></url>
                    </urlset>
                    """.formatted(base, base);
            writeText(exchange, 200, "application/xml", xml);
        }
    }

    private static final class SiteHandler implements HttpHandler {

        private final int port;

        private SiteHandler(int port) {
            this.port = port;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var requestUri = exchange.getRequestURI();
            var path = requestUri.getPath();
            var parts = splitPath(path);

            // /site/{scenario}/seed/{seed}/root
            if (parts.size() == 5 && "site".equals(parts.get(0))
                    && "seed".equals(parts.get(2))
                    && "root".equals(parts.get(4))) {
                String scenario = decode(parts.get(1));
                long seed = parseLong(parts.get(3), 42L);
                var params = parseQuery(requestUri);
                renderPage(exchange, scenario, seed, 0, 0, params);
                return;
            }

            // /site/{scenario}/seed/{seed}/page/{depth}/{node}
            if (parts.size() == 7 && "site".equals(parts.get(0))
                    && "seed".equals(parts.get(2))
                    && "page".equals(parts.get(4))) {
                String scenario = decode(parts.get(1));
                long seed = parseLong(parts.get(3), 42L);
                int depth = parseInt(parts.get(5), 0);
                int node = parseInt(parts.get(6), 0);
                var params = parseQuery(requestUri);
                renderPage(exchange, scenario, seed, depth, node, params);
                return;
            }

            writeText(exchange, 404, "text/plain",
                    "Unknown /site route: " + path);
        }

        private void renderPage(HttpExchange exchange, String scenario,
                long seed,
                int depth, int node, Map<String, String> params)
                throws IOException {
            int maxDepth = parseInt(params.get("depth"), 4);
            int branching = parseInt(params.get("branch"), 8);
            int avgSize = parseInt(params.get("avgSize"), 32 * 1024);
            double duplicatePct = parseDouble(params.get("dupPct"), 0.1d);
            double nonCanonicalPct =
                    parseDouble(params.get("nonCanonicalPct"), 0.05d);
            double pdfLinkPct = parseDouble(params.get("pdfLinkPct"), 0.08d);

            var links = new ArrayList<String>();
            if (depth < maxDepth) {
                for (int i = 0; i < branching; i++) {
                    int childNode = (node * branching) + i;
                    String target = "/site/%s/seed/%d/page/%d/%d"
                            .formatted(scenario, seed, depth + 1, childNode);
                    double draw = draw(seed, scenario, depth, node, i);
                    if (draw < pdfLinkPct) {
                        target = "/asset/%s/seed/%d/pdf/%d"
                                .formatted(scenario, seed, childNode);
                    }
                    if (draw > (1.0d - nonCanonicalPct)) {
                        target += "?utm_source=bench&utm_campaign=" + scenario
                                + "&v=" + i;
                    }
                    links.add(target);
                }
            }

            int duplicateBucket = Math.max(1,
                    (int) Math.round(1d / Math.max(0.001d, duplicatePct)));
            int contentBucket = Math.floorMod(node, duplicateBucket);

            var html = new StringBuilder(avgSize + 2048);
            html.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                    .append("<title>")
                    .append(escapeHtml(scenario)).append("-d").append(depth)
                    .append("-n").append(node)
                    .append("</title></head><body>")
                    .append("<h1>Scenario ").append(escapeHtml(scenario))
                    .append("</h1>")
                    .append("<p>seed=").append(seed)
                    .append(", depth=").append(depth)
                    .append(", node=").append(node)
                    .append(", duplicateBucket=").append(contentBucket)
                    .append("</p>");

            int payloadTarget = Math.max(1024, avgSize - html.length());
            appendPayload(html, seed, scenario, depth, contentBucket,
                    payloadTarget);

            for (String link : links) {
                html.append("<a href=\"").append(escapeHtml(link))
                        .append("\">next</a>\n");
            }

            html.append("</body></html>");
            writeText(exchange, 200, "text/html; charset=utf-8",
                    html.toString());
        }
    }

    private static final class AssetHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var path = exchange.getRequestURI().getPath();
            var parts = splitPath(path);

            // /asset/{scenario}/seed/{seed}/{kind}/{id}
            if (parts.size() != 6 || !"asset".equals(parts.get(0))
                    || !"seed".equals(parts.get(2))) {
                writeText(exchange, 404, "text/plain",
                        "Unknown /asset route: " + path);
                return;
            }

            String scenario = decode(parts.get(1));
            long seed = parseLong(parts.get(3), 42L);
            String kind = decode(parts.get(4));
            int id = parseInt(parts.get(5), 0);

            if ("pdf".equalsIgnoreCase(kind)) {
                byte[] bytes = generatePdfLike(seed, scenario, id, 16 * 1024);
                writeBytes(exchange, 200, "application/pdf", bytes);
                return;
            }
            byte[] bytes = generateBinary(seed, scenario, id, 64 * 1024);
            writeBytes(exchange, 200, "application/octet-stream", bytes);
        }
    }

    private static void appendPayload(StringBuilder builder, long seed,
            String scenario, int depth, int contentBucket, int targetLen) {
        var random =
                new Random(Objects.hash(seed, scenario, depth, contentBucket));
        while (builder.length() < targetLen) {
            builder.append(" lorem-")
                    .append(Integer.toHexString(random.nextInt()))
                    .append(' ');
        }
    }

    private static byte[] generatePdfLike(long seed, String scenario, int id,
            int size) {
        String header = "%PDF-1.4\n"
                + "% Synthetic fixture PDF\n"
                + "% seed=" + seed + " scenario=" + scenario + " id=" + id
                + "\n";
        var payload = header.getBytes(StandardCharsets.US_ASCII);
        if (payload.length >= size) {
            return payload;
        }
        var bytes = new byte[size];
        System.arraycopy(payload, 0, bytes, 0, payload.length);
        var random = new Random(Objects.hash(seed, scenario, id, "pdf"));
        for (int i = payload.length; i < bytes.length; i++) {
            bytes[i] = (byte) (32 + random.nextInt(90));
        }
        return bytes;
    }

    private static byte[] generateBinary(long seed, String scenario, int id,
            int size) {
        var bytes = new byte[Math.max(1024, size)];
        var random = new Random(Objects.hash(seed, scenario, id, "bin"));
        random.nextBytes(bytes);
        return bytes;
    }

    private static List<String> splitPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return List.of();
        }
        String clean = path.startsWith("/") ? path.substring(1) : path;
        if (clean.isEmpty()) {
            return List.of();
        }
        String[] parts = clean.split("/");
        var list = new ArrayList<String>(parts.length);
        Collections.addAll(list, parts);
        return list;
    }

    private static Map<String, String> parseQuery(URI uri) {
        var query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        var map = new HashMap<String, String>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = decode(pair.substring(0, idx));
                String value = decode(pair.substring(idx + 1));
                map.put(key, value);
            } else {
                map.put(decode(pair), "");
            }
        }
        return map;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
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

    private static double draw(long seed, String scenario, int depth, int node,
            int branch) {
        var random =
                new Random(Objects.hash(seed, scenario, depth, node, branch));
        return random.nextDouble();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void writeText(HttpExchange exchange, int status,
            String contentType, String body) throws IOException {
        writeBytes(exchange, status, contentType,
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(HttpExchange exchange, int status,
            String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
