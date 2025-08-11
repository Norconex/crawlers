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
package com.norconex.crawler.core2.mocks.cli;

import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public final class MockCliEventWriter implements EventListener<Event> {

    private Path eventFile;

    //    public static final List<String> EVENTS = new ArrayList<>();

    @Override
    public void accept(Event event) {
        try {
            Files.writeString(
                    eventFile, event.getName() + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            fail("accept --> Oups!", e);
        }
        //        EVENTS.add(event.getName());
    }

    static List<String> parseEvents(Path eventFile) {
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
