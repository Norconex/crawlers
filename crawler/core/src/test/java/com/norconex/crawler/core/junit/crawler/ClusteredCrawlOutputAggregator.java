package com.norconex.crawler.core.junit.crawler;

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
import com.norconex.crawler.core.junit.cluster.SharedClusterClient;
import com.norconex.crawler.core.junit.crawler.ClusteredCrawlOuput.CrawlNode;
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

                // Only try to load caches if a config was provided
                if (cfg != null && output.getCaches().isEmpty()) {
                    Path localZip = Files.createTempFile("cache", ".zip");
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
        Path eventFile = cfg.getEventListeners().stream()
                .filter(MockCliEventWriter.class::isInstance)
                .findFirst()
                .map(MockCliEventWriter.class::cast)
                .map(ew -> ew.getEventFile())
                .orElse(null);

        if (eventFile == null) {
            return List.of();
        }

        try {
            System.err.println("XXX REMOTE EVENTS FILE PATH: " + eventFile);
            Path localEventsFile = Files.createTempFile(null, null);
            System.err
                    .println("XXX LOCAL EVENTS FILE PATH: " + localEventsFile);

            // Retry logic to handle filesystem sync delays in containers
            int maxRetries = 5;
            long retryDelayMs = 100;
            NotFoundException lastException = null;

            for (int attempt = 0; attempt < maxRetries; attempt++) {
                try {
                    container.copyFileFromContainer(
                            eventFile.toString().replace('\\', '/'),
                            localEventsFile.toString());
                    System.err.println("XXX LOCAL EVENTS FILE CONTENT: "
                            + Files.readString(localEventsFile));
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
            System.err.println(
                    "XXX EVENTS FAILURE (file not found after retries):");
            e.printStackTrace(System.err);
            throw new RuntimeException("Could not read events file after "
                    + "retries: " + eventFile, e);
        } catch (Throwable e) {
            System.err.println("XXX EVENTS FAILURE:");
            e.printStackTrace(System.err);
            throw new RuntimeException("Could not read events file.", e);
        }
    }

    private static Map<String, List<JsonNode>>
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
}
