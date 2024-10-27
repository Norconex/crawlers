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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.event.CrawlerEvent;

public final class MockCliEventWriter
        implements EventListener<Event> {

    public static final String EVENTS_FILE_NAME = "events.txt";

    private Path eventFile;

    @Override
    public void accept(Event event) {
        if (event.is(CrawlerEvent.CRAWLER_INIT_BEGIN)) {
            eventFile = ((CrawlerEvent) event)
                    .getSource()
                    .getWorkDir()
                    .resolve(EVENTS_FILE_NAME);
        }

        try {
            Files.writeString(
                    eventFile,
                    event.getName() + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
