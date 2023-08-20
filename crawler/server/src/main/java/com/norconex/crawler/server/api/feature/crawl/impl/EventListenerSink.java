/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.server.api.feature.crawl.impl;

import java.util.function.Function;
import java.util.function.Predicate;

import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.FluxSink;

/**
 * Adds matching crawler events to a Flux Sink.
 */
@RequiredArgsConstructor
@AllArgsConstructor
public class EventListenerSink implements EventListener<Event> {

    @NonNull
    private final FluxSink<Object> sink;
    @NonNull
    private final Predicate<Event> predicate;

    private Function<Event, Object> transformer;

    @Override
    public void accept(Event event) {
        if (predicate.test(event)) {
            sink.next(transformer == null ? event : transformer.apply(event));
        }
    }
}
