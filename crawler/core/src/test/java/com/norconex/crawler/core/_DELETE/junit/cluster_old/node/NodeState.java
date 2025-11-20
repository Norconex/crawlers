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
package com.norconex.crawler.core._DELETE.junit.cluster_old.node;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;

import com.norconex.commons.lang.map.Properties;

import lombok.extern.slf4j.Slf4j;

/**
 * Maintains shared runtime state between cluster nodes (running in separate
 * JVMs) and test methods. State is persisted to the file system so it can
 * be read across JVM boundaries. State scope (i.e., this class) is per-JVM.
 */
@Slf4j
@Deprecated
public class NodeState {

    public static final String NODE_STARTED_AT = "nodeStartedAt";
    public static final String NODE_COUNT = "nodeCount";
    public static final String CRAWL_PIPELINE_CREATED = "crawlPipelineCreated";

    private static final String STATE_FILE_NAME = "state.properties";

    private static final Properties props = new Properties();
    private static int nodeIndex;

    private static Path file;
    private static final Object lock = new Object();
    private static final ConcurrentHashMap<Path, Object> fileLocks =
            new ConcurrentHashMap<>();

    public static void init(Path workDir) {
        if (file != null) {
            throw new IllegalStateException("Already initialized.");
        }
        file = workDir.resolve(STATE_FILE_NAME);
        try {
            FileUtils.touch(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        props.addMapChangeListener(event -> write());
        props.set(NODE_STARTED_AT, LocalDateTime.now().toString());
    }

    /**
     * To use by crawler nodes.
     * @return state props
     */
    public static Properties props() {
        return props;
    }

    /**
     * To use by crawler nodes.
     * @return state props
     */
    public static int nodeIndex() {
        return nodeIndex;
    }

    /**
     * Loads state from disk (test-side operation). This allows the test
     * method to read state written by nodes in other JVMs.
     * @param nodeWorkDir crawler working directory
     * @return properties
     */
    public static Properties load(Path nodeWorkDir) {
        var loadedProps = new Properties();
        var stateFile = nodeWorkDir.resolve(STATE_FILE_NAME);
        if (Files.exists(stateFile)) {
            try (Reader r = Files.newBufferedReader(stateFile)) {
                loadedProps.loadFromProperties(r);
                LOG.info("Loaded state props from {}: {}", stateFile,
                        loadedProps);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            LOG.info("State file {} does not exist when loading.", stateFile);
        }
        return loadedProps;
    }

    private static void write() {
        LOG.info("Persist node state props: " + props);
        var fileLock = fileLocks.computeIfAbsent(file, k -> new Object());
        synchronized (fileLock) {
            var tempFile = file.getParent()
                    .resolve(file.getFileName() + ".tmp." +
                            System.currentTimeMillis());
            try {
                // Write to temp file
                try (Writer w = Files.newBufferedWriter(
                        tempFile,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    props.storeToProperties(w);
                    w.flush();
                }
                // Atomic move with retry logic
                var attempts = 0;
                final var maxAttempts = 5;
                while (true) {
                    try {
                        Files.move(
                                tempFile,
                                file,
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                        // Force sync to disk
                        try (var channel =
                                java.nio.channels.FileChannel.open(file,
                                        java.nio.file.StandardOpenOption.WRITE)) {
                            channel.force(true);
                        }
                        LOG.info("State file {} written and synced.", file);
                        break;
                    } catch (AccessDeniedException ade) {
                        attempts++;
                        if (attempts >= maxAttempts) {
                            throw ade;
                        }
                        LOG.warn("AccessDeniedException moving {} to {}. " +
                                "Retrying ({}/{})...", tempFile, file, attempts,
                                maxAttempts);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new UncheckedIOException(new IOException(
                                    "Interrupted during file move retry", ie));
                        }
                    }
                }
            } catch (IOException e) {
                // Clean up temp file on failure
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    //NOOP
                }
                throw new UncheckedIOException(e);
            }
        }
    }
}
