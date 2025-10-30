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
package com.norconex.crawler.core.mocks.cli;

import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public final class MockCliEventWriter implements EventListener<Event> {

    // Use String instead of Path for reliable serialization across containers
    private String eventFilePath;

    public void setEventFile(Path path) {
        if (path != null) {
            // Convert to forward slashes for cross-platform compatibility
            this.eventFilePath = path.toString().replace('\\', '/');
        }
    }

    public Path getEventFile() {
        return eventFilePath != null ? Paths.get(eventFilePath) : null;
    }

    @Override
    public void accept(Event event) {
        if (eventFilePath == null) {
            LOG.warn("Event file path is null, skipping event: {}",
                    event.getName());
            return;
        }

        try {
            Path eventFile = Paths.get(eventFilePath);
            System.err.println("XXX WRITING event %s to file: %s"
                    .formatted(event.getName(), eventFile));
            // Ensure parent directory exists
            if (eventFile.getParent() != null) {
                Files.createDirectories(eventFile.getParent());
            }
            Files.writeString(
                    eventFile, event.getName() + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND,
                    StandardOpenOption.SYNC);
        } catch (IOException e) {
            fail("accept --> Oups!", e);
        }
    }

    public static List<String> parseEvents(Path eventFile) {
        if (!Files.exists(eventFile)) {
            LOG.info("Temporary test events file not found: {}", eventFile);
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
