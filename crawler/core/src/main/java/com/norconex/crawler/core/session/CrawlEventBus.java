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
package com.norconex.crawler.core.session;

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventManager;

import lombok.RequiredArgsConstructor;

/**
 * Thin wrapper over {@link EventManager} for firing crawler events.
 * Can be injected directly in tests without the full {@link CrawlSession}.
 */
@RequiredArgsConstructor
public class CrawlEventBus {

    private final EventManager eventManager;

    /**
     * Fires the given event through the underlying event manager.
     * @param event the event to fire
     */
    public void fire(Event event) {
        eventManager.fire(event);
    }
}
