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
package com.norconex.crawler.core.junit.clusternew.node;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import org.apache.commons.io.FileUtils;

import com.healthmarketscience.jackcess.RuntimeIOException;
import com.norconex.commons.lang.map.Properties;

import lombok.extern.slf4j.Slf4j;

/**
 * Maintains shared runtime state between cluster nodes (running in separate
 * JVMs) and test methods. State is persisted to the file system so it can
 * be read across JVM boundaries. State scope (i.e., this class) is per-JVM.
 */
@Slf4j
public class NodeState {

    public static final String NODE_STARTED_AT = "nodeStartedAt";
    public static final String NODE_COUNT_AT_JOIN = "nodeCountAtJoin";

    private static final String STATE_FILE_NAME = "state.properties";

    private static final Properties props = new Properties();
    private static int nodeIndex;

    private static Path file;
    private static final Object lock = new Object();

    public static void init(Path workDir) {
        if (file != null) {
            throw new IllegalStateException("Already initialized.");
        }
        file = workDir.resolve(STATE_FILE_NAME);
        try {
            FileUtils.touch(file.toFile());
        } catch (IOException e) {
            throw new RuntimeIOException(e);
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
     * Clears all state and resets for a new test. Does not delete the
     * file, but clears the in-memory state.
     */
    public static void reset() {
        synchronized (lock) {
            props.clear();
            file = null;
        }
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
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }
        return loadedProps;
    }

    private static void write() {
        LOG.debug("Persist node state props: " + props);
        synchronized (lock) {
            // Use a temp file and atomic move to reduce corruption risk
            // when multiple JVMs write concurrently (which should not happen)
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
                }
                // Atomic move (overwrites target)
                Files.move(
                        tempFile,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // Clean up temp file on failure
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    //NOOP
                }
                throw new RuntimeIOException(e);
            }
        }
    }
}
