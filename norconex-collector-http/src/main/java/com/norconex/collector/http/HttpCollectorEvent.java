/* Copyright 2018 Norconex Inc.
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
package com.norconex.collector.http;

import com.norconex.collector.core.CollectorEvent;

/**
 * An HTTP Collector Event.
 * @author Pascal Essiembre
 */
public class HttpCollectorEvent extends CollectorEvent<HttpCollector> {

    private static final long serialVersionUID = 1L;

    //TODO really keep since no custom event?

    /**
     * New HTTP collector event.
     * @param name event name
     * @param source collector responsible for triggering the event
     * @param exception exception tied to this event (may be <code>null</code>)
     */
    public HttpCollectorEvent(
            String name, HttpCollector source, Throwable exception) {
        super(name, source, exception);
    }

    public static HttpCollectorEvent create(
            String name, HttpCollector collector) {
        return create(name, collector, null);
    }
    public static HttpCollectorEvent create(
            String name, HttpCollector collector, Throwable exception) {
        return new HttpCollectorEvent(name, collector, exception);
    }

}
