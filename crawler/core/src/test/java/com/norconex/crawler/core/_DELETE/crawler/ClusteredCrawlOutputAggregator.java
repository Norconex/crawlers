package com.norconex.crawler.core._DELETE.crawler;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.exception.NotFoundException;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core._DELETE.clusterold.SharedClusterClient;
import com.norconex.crawler.core._DELETE.crawler.ClusteredCrawlOuput.CrawlNode;
import com.norconex.crawler.core.mocks.cli.MockCliEventWriter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ClusteredCrawlOutputAggregator {

    private ClusteredCrawlOutputAggregator() {
    }

    public static ClusteredCrawlOuput aggregate(
            List<ExecResult> execResults,
            SharedClusterClient client,
            CrawlConfig cfg) {
        var output = new ClusteredCrawlOuput(
                execResults.stream().map(ex -> new CrawlNode()
                        .setStdout(ex.getStdout())
                        .setStderr(ex.getStderr())
                        .setExitCode(ex.getExitCode()))
                        .toList());
        populateOutput(output, client, cfg);
        return output;
    }

    private static void populateOutput(
            ClusteredCrawlOuput output,
            SharedClusterClient client,
            CrawlConfig cfg) {
        // We are on host

        var workDirStr = client.getNodeWorkdir().toString().replace('\\', '/');
        var cachesDirStr = workDirStr + "/"
                + CachesExporterCrawlDriverWrapper.EXPORT_REL_DIR;

        for (var i = 0; i < client.getNodes().size(); i++) {
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

                // Only try to load caches if a config was provided
                if (cfg != null && output.getCaches().isEmpty()) {
                    var localZip = Files.createTempFile("cache", ".zip");
                    container.copyFileFromContainer(
                            cachesDirStr + "/" + cfg.getId() + ".zip",
                            localZip.toString());
                    Optional.ofNullable(loadCaches(localZip)).ifPresent(
                            map -> output.getCaches().putAll(map));
                }

                // load events
                node.getEvents().addAll(loadNodeEvents(container, client, cfg));
            } catch (NotFoundException e) {
                LOG.warn("Cache file(s) not found: ", e.getMessage());
            } catch (UnsupportedOperationException | IOException
                    | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static List<String> loadNodeEvents(
            GenericContainer<?> container,
            SharedClusterClient client,
            CrawlConfig cfg) {
        var eventFile = cfg.getEventListeners().stream()
                .filter(MockCliEventWriter.class::isInstance)
                .findFirst()
                .map(MockCliEventWriter.class::cast)
                .map(MockCliEventWriter::getEventFile)
                .orElse(null);

        if (eventFile == null) {
            return List.of();
        }

        try {
            var localEventsFile = Files.createTempFile(null, null);

            // Retry logic to handle filesystem sync delays in containers
            var maxRetries = 5;
            var retryDelayMs = 100L;
            NotFoundException lastException = null;

            for (var attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    container.copyFileFromContainer(
                            eventFile.toString().replace('\\', '/'),
                            localEventsFile.toString());
                    return MockCliEventWriter.parseEvents(localEventsFile);
                } catch (NotFoundException e) {
                    lastException = e;
                    if (attempt < maxRetries - 1) {
                        LOG.warn("Events file not found on attempt {}/{}, "
                                + "retrying in {}ms...",
                                attempt + 1, maxRetries, retryDelayMs);
                        Thread.sleep(retryDelayMs);
                        retryDelayMs *= 2; // Exponential backoff
                    }
                }
            }

            // If we exhausted retries, throw the last exception
            throw lastException;
        } catch (NotFoundException e) {
            e.printStackTrace(System.err);
            throw new RuntimeException("Could not read events file after "
                    + "retries: " + eventFile, e);
        } catch (Throwable e) {
            throw new RuntimeException("Could not read events file.", e);
        }
    }

    private static Map<String, List<JsonNode>>
            loadCaches(Path zipFile) throws IOException {
        if (!Files.exists(zipFile)) {
            return null;
        }
        var mapper = new ObjectMapper();
        Map<String, List<JsonNode>> jsonContents = new HashMap<>();
        try (var zis =
                new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                var baos = new ByteArrayOutputStream();
                var buffer = new byte[1024];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                var jsonString = baos.toString(StandardCharsets.UTF_8);
                try {
                    var node = mapper.readTree(jsonString);
                    var storeKey = node.get("store").asText();
                    var recordsNode = node.get("records");
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
}
