/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.junit.crawler;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.testcontainers.containers.Container.ExecResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.core.junit.cluster.SharedCluster;
import com.norconex.crawler.core.junit.cluster.SharedClusterClient;
import com.norconex.crawler.core.junit.crawler.ClusteredCrawlOuput.CrawlNode;
import com.norconex.crawler.core.mocks.crawler.MockCrawlDriverFactory;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.ExecUtil;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;

/**
 * Launches a crawler with supplied arguments to all nodes in a cluster.
 */
@Slf4j
@Builder
public final class ClusteredCrawler {

    @Default
    private final Class<? extends Supplier<CrawlDriver>> driverSupplierClass =
            MockCrawlDriverFactory.class;

    @SuppressWarnings("unchecked")
    @Generated // excluded from coverage
    public static void main(String[] args) {
        // We are on a cluster node
        CrawlDriver driver = null;
        try {
            driver = ((Supplier<CrawlDriver>) Class.forName(args[0])
                    .getDeclaredConstructor()
                    .newInstance()).get();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid crawl driver class: " + args[0]);
        }

        var cleanArgs = ArrayUtils.remove(args, 0);
        try {
            CliCrawlerLauncher.launch(
                    CachesExportCrawlDriverWrapper.wrap(driver), cleanArgs);
            // Explicitly exit with success code to terminate the JVM
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            // Exit with error code on failure
            System.exit(1);
        }
    }

    public ClusteredCrawlOuput launch(
            int numOfNodes, CrawlConfig config, String... extraArgs) {
        // We are on host
        return SharedCluster.withNodesAndGet(numOfNodes, client -> {
            try {
                var execResults = doLaunchCrawler(client, config, extraArgs);

                // Handle timeout case - return early without processing results
                if (execResults.isEmpty()) {
                    LOG.warn("No execution results available (likely timeout), "
                            + "returning empty output");
                    return new ClusteredCrawlOuput(new ArrayList<>());
                }

                var output = new ClusteredCrawlOuput(
                        execResults.stream().map(ex -> new CrawlNode()
                                .setStdout(ex.getStdout())
                                .setStderr(ex.getStderr())
                                .setExitCode(ex.getExitCode()))
                                .toList());
                gatherOutput(output, client, config);
                return output;
            } finally {
                // Kill any running Java processes in containers after test completes
                cleanupJavaProcesses(client);
            }
        });
    }

    private List<ExecResult> doLaunchCrawler(
            SharedClusterClient client, CrawlConfig cfg, String... extraArgs) {
        // We are on host

        var cp = SharedCluster.buildNodeClasspath();
        var cmdArgs = new ArrayList<String>();
        cmdArgs.add("java");
        var debug = ExecUtil.isDebugMode();
        if (debug) {
            cmdArgs.add("-agentlib:jdwp=transport=dt_socket,"
                    + "server=y,suspend=n,address=*:5005");
        }
        var log4jCfg = findNodeLog4j2Config();
        if (log4jCfg != null) {
            cmdArgs.add("-Dlog4j2.configurationFile=" + log4jCfg);
        }
        cmdArgs.add("-Dfile.encoding=UTF8");
        cmdArgs.add("-Djava.net.preferIPv4Stack=true");
        cmdArgs.add("-cp");
        cmdArgs.add(cp);

        // Prepare config and resolved workdir
        if (cfg != null) {
            if (StringUtils.isBlank(cfg.getId())) {
                // Use nanoTime + random for better uniqueness to avoid
                // cache collisions from previous test runs
                cfg.setId("clustered-" + System.nanoTime() + "-"
                        + (int) (Math.random() * 10000));
            }
            cfg.setWorkDir(client.getNodeWorkdir());
        }

        // Build command
        cmdArgs.add(ClusteredCrawler.class.getName());
        cmdArgs.add(driverSupplierClass.getName());
        cmdArgs.addAll(List.of(extraArgs));
        if (cfg != null) {
            cmdArgs.add("-config");
            var cfgPath = writeConfigOnCluster(client, cfg)
                    .toString()
                    .replace('\\', '/');
            cmdArgs.add(cfgPath);
        }

        var responses = client.execOnCluster(cmdArgs.toArray(new String[] {}));

        try {
            // Wait up to 1 minute for crawlers to complete
            // If they take longer, the finally block will kill them
            return ConcurrentUtil.allOf(responses)
                    .get(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            throw ConcurrentUtil.wrapAsCompletionException(e);
        } catch (java.util.concurrent.TimeoutException e) {
            LOG.warn("Crawler execution timed out after 60 seconds, "
                    + "will kill processes and gather partial results");
            // Return empty list, cleanup will happen in finally block
            return new ArrayList<>();
        }
    }

    private void gatherOutput(
            ClusteredCrawlOuput output,
            SharedClusterClient client,
            CrawlConfig cfg) {
        // We are on host

        var workDirStr = client.getNodeWorkdir().toString().replace('\\', '/');
        var cachesDirStr = workDirStr + "/"
                + CachesExportCrawlDriverWrapper.EXPORT_REL_DIR;

        for (int i = 0; i < client.getNodes().size(); i++) {
            var container = client.getNodes().get(i);
            var node = output.getNodes().get(i);
            node.setName(container.getNetworkAliases().get(0));
            try {
                // Base workdir listing
                var res = container.execInContainer(
                        "find", workDirStr,
                        "-maxdepth", "2", "-type", "f");

                //TODO check if workdir is created at crawler start up and if so, if the path is normalized for the OS (\ vs /)

                node.setWorkdirFiles(PathListParser.buildTreeFromPathList(
                        new ArrayList<>(res.getStdout().lines().toList())));

                if (output.getCaches().isEmpty()) {
                    Path localZip = Files.createTempFile("cache", ".zip");
                    container.copyFileFromContainer(
                            cachesDirStr + "/" + cfg.getId() + ".zip",
                            localZip.toString());
                    Optional.ofNullable(loadCaches(localZip)).ifPresent(
                            map -> output.getCaches().putAll(map));
                }

            } catch (UnsupportedOperationException | IOException
                    | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Map<String, List<JsonNode>>
            loadCaches(Path zipFile) throws IOException {
        if (!Files.exists(zipFile)) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<JsonNode>> jsonContents = new HashMap<>();
        try (ZipInputStream zis =
                new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                String jsonString = baos.toString(StandardCharsets.UTF_8);
                try {
                    JsonNode node = mapper.readTree(jsonString);
                    String storeKey = node.get("store").asText();
                    JsonNode recordsNode = node.get("records");
                    List<JsonNode> recordsList = new ArrayList<>();
                    if (recordsNode != null && recordsNode.isArray()) {
                        for (JsonNode item : recordsNode) {
                            recordsList.add(item);
                        }
                    }
                    jsonContents.put(storeKey, recordsList);
                } catch (JsonProcessingException e) {
                    throw new IOException("Failed to parse JSON for entry: "
                            + entry.getName(), e);
                }
                zis.closeEntry();
            }
        }

        return jsonContents;
    }

    private Path writeConfigOnCluster(
            SharedClusterClient client, CrawlConfig config) {
        var w = new StringWriter();
        BeanMapper.DEFAULT.write(config, w, Format.YAML);
        var yaml = w.toString();

        // Force POSIX workDir in YAML (avoid Windows backslashes)
        var nodeWorkDir = client.getNodeWorkdir().toString()
                .replace('\\', '/');
        yaml = yaml.replaceAll(
                "(?m)^workDir:.*$",
                "workDir: \"" + nodeWorkDir + "\"");
        return client.copyStringToClusterFile(yaml, "config.yaml");
    }

    /**
     * Attempts to find a staged log4j2.xml and returns the container path to it
     * if present; otherwise returns null.
     */
    private String findNodeLog4j2Config() {
        try {
            var dirs = Files.list(SharedCluster.HOST_LIB_DIR)
                    .filter(Files::isDirectory)
                    .toList();
            for (String fileName : List.of("log4j2-test.xml", "log4j2.xml")) {
                for (var d : dirs) {
                    var logCfg = d.resolve(fileName);
                    if (Files.exists(logCfg)) {
                        return "file:" + SharedCluster.NODE_LIB_DIR + "/"
                                + d.getFileName() + "/" + fileName;
                    }
                }
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed locating log4j2.xml in staged libs.", e);
        }
    }

    private void cleanupJavaProcesses(SharedClusterClient client) {
        try {
            // Kill all java processes that might still be running
            client.getNodes().forEach(node -> {
                try {
                    node.execInContainer("pkill", "-9", "java");
                } catch (Exception e) {
                    LOG.debug(
                            "Could not kill java processes in container {}: {}",
                            node.getNetworkAliases().get(0), e.getMessage());
                }
            });
        } catch (Exception e) {
            LOG.warn("Failed to cleanup java processes", e);
        }
    }
}
