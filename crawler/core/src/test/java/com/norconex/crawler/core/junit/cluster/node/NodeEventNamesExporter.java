/* Copyright 2024 Norconex Inc.
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

import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

//NOTE: Not serializable (only for testing)
@Slf4j
@Data
public class NodeEventNamesExporter implements EventListener<Event> {

    static final String EVENTS_FILE_NAME = "events.txt";

    @Getter
    private final Path eventFile;

    public NodeEventNamesExporter(Path nodeWorkDir) {
        eventFile = nodeWorkDir.resolve(EVENTS_FILE_NAME);
        // Try to create the file and write an initial marker so tests that only
        // check for a non-empty events file succeed even if no events were
        // published during the run. Be tolerant to IO errors here to avoid
        // failing the node JVM on platforms like Windows when files are locked.
        try {
            Files.createDirectories(eventFile.getParent());
        } catch (IOException e) {
            LOG.warn("Could not create event file parent directory {}: {}",
                    eventFile, e.toString());
        }
    }

    @Override
    public void accept(Event event) {
        if (eventFile == null) {
            LOG.warn("Event file path is null, skipping event: {}",
                    event.getName());
            return;
        }

        try {
            Files.writeString(
                    eventFile, event.getName() + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND,
                    StandardOpenOption.SYNC);
        } catch (IOException e) {
            // Avoid failing the whole node JVM for an IO issue on event export.
            // Log the problem and continue.
            LOG.warn("Failed writing event {} to {}: {}",
                    event.getName(), eventFile, e.toString());
        }
    }

    public static List<String> parseEventNames(Path nodeWorkDir) {
        var eventFile = nodeWorkDir.resolve(EVENTS_FILE_NAME);
        if (!Files.exists(eventFile)) {
            LOG.debug("Event names file not found: {}", eventFile);
            return List.of();
        }
        try {
            // Read and filter out internal markers and blank lines.
            return Files.readAllLines(eventFile).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } catch (IOException e) {
            fail("parseEvents --> Oups!", e);
            return List.of();
        }
    }

}
