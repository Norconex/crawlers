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
package com.norconex.crawler.core.cluster.impl.infinispan;

import java.util.function.BiConsumer;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryModifiedEvent;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Listener(clustered = true)
@RequiredArgsConstructor
public class PipelineStepChangeListener {

    @NonNull
    private final BiConsumer<String, StepRecord> stepRecCallback;

    @CacheEntryCreated
    public void onCreated(
            CacheEntryCreatedEvent<String, StepRecord> e) {
        if (!e.isPre()) {
            react(e.getKey(), e.getValue());
        }
    }

    @CacheEntryModified
    public void onModified(
            CacheEntryModifiedEvent<String, StepRecord> e) {
        if (!e.isPre()) {
            react(e.getKey(), e.getNewValue());
        }
    }

    private void react(String key, StepRecord stepRec) {
        stepRecCallback.accept(key, stepRec);
    }
}
