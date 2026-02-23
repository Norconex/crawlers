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
package com.norconex.crawler.core.junit.cluster.node;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicLong;

import com.norconex.crawler.core.junit.cluster.state.StateDbClient;

/**
 * Reads from an InputStream and stores it in the state database.
 * Buffers decoded UTF-8 characters, and saves to
 * database every 4048 characters. Flushes any remaining data at end of stream.
 * Used in CrawlerNode on started process.
 */
public class StreamCapturer implements Runnable {
    private static final int CHUNK_SIZE = 100 * 1024;
    private final Reader reader;
    private final String topic;
    private final AtomicLong rowCounter = new AtomicLong();
    private final StateDbClient stateDb;

    public StreamCapturer(Reader reader, String nodeName, String topic) {
        this.reader = reader;
        this.topic = topic;
        // Only initialize state DB client for non-log topics.
        // stdout/stderr are now written to per-node log files directly
        // by JvmProcess, to avoid H2 growing large.
        this.stateDb = ("stdout".equals(topic) || "stderr".equals(topic))
                ? null
                : StateDbClient.get().asNode(nodeName);
    }

    @Override
    public void run() {
        if (stateDb == null) {
            // Nothing to persist for stdout/stderr; just drain the stream
            // to avoid blocking the child process.
            drainQuietly();
            return;
        }
        var buffer = new StringBuilder();
        try {
            int ch;
            while ((ch = reader.read()) != -1) {
                buffer.append((char) ch);
                if (buffer.length() >= CHUNK_SIZE) {
                    saveToDatabase(buffer.substring(0, CHUNK_SIZE));
                    buffer.delete(0, CHUNK_SIZE);
                }
            }
            if (!buffer.isEmpty()) {
                saveToDatabase(buffer.toString());
                buffer.setLength(0);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (!buffer.isEmpty()) {
                saveToDatabase(buffer.toString());
                buffer.setLength(0);
            }
        }
    }

    private void drainQuietly() {
        try {
            while (reader.read() != -1) {
                // discard
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void saveToDatabase(String logChunk) {
        if (stateDb != null) {
            stateDb.put(topic, "" + rowCounter.incrementAndGet(), logChunk);
        }
    }
}
