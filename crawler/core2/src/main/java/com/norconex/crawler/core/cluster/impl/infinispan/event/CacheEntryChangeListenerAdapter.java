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
package com.norconex.crawler.core.cluster.impl.infinispan.event;

import org.infinispan.notifications.Listener;
import org.infinispan.notifications.Listener.Observation;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryCreated;
import org.infinispan.notifications.cachelistener.annotation.CacheEntryModified;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Listener(clustered = true, observation = Observation.POST)
@RequiredArgsConstructor
public class CacheEntryChangeListenerAdapter<T> {
    @Getter
    @NonNull
    private final CacheEntryChangeListener<T> delegate;

    @CacheEntryCreated
    @CacheEntryModified
    public void onEntryChanged(CacheEntryEvent<String, T> event) {
        if (event.getValue() != null) {
            delegate.onEntryChanged(event.getKey(), event.getValue());
        }
    }
}