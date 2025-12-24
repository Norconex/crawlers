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
package com.norconex.crawler.core.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;

/**
 * Event listener that queues all emitted event names in memory.
 */
public class EventNameRecorder implements EventListener<Event> {

    private final Queue<String> events = new ConcurrentLinkedQueue<>();

    @Override
    public void accept(Event event) {
        if (event == null) {
            return;
        }
        events.add(event.getName());

    }

    public List<String> getNames() {
        return new ArrayList<>(events);
    }

}
