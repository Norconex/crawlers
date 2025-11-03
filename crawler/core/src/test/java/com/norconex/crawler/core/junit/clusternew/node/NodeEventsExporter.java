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
package com.norconex.crawler.core.junit.clusternew.node;

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
final class NodeEventsExporter implements EventListener<Event> {

    static final String EVENTS_FILE_NAME = "events.txt";

    @Getter
    private final Path eventFile;

    public NodeEventsExporter(Path nodeWorkDir) {
        eventFile = nodeWorkDir.resolve(EVENTS_FILE_NAME);
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
            fail("accept --> Oups!", e);
        }
    }

    public static List<String> parseEvents(Path nodeWorkDir) {
        var eventFile = nodeWorkDir.resolve(EVENTS_FILE_NAME);
        if (!Files.exists(eventFile)) {
            LOG.info("Test events file not found: {}", eventFile);
            return List.of();
        }
        try {
            return Files.readAllLines(eventFile);
        } catch (IOException e) {
            fail("parseEvents --> Oups!", e);
            return List.of();
        }
    }

}
