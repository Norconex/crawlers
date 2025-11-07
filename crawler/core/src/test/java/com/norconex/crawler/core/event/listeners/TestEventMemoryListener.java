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
package com.norconex.crawler.core.event.listeners;

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;

import lombok.extern.slf4j.Slf4j;

/**
 * Add this listener to the crawler config to capture and store events
 * in memory, within the same thread (and child threads).
 * Used in conjunction with {@link TestEventMemory}.
 */
@Slf4j
public class TestEventMemoryListener implements EventListener<Event> {

    private boolean warned;

    @Override
    public void accept(Event event) {
        if (!warned) {
            LOG.info("Be careful: this test is storing events in memory.");
            warned = true;
        }
        TestEventMemory.add(event);
    }
}
