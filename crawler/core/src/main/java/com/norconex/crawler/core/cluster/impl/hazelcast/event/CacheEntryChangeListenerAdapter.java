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
package com.norconex.crawler.core.cluster.impl.hazelcast.event;

import java.util.UUID;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryUpdatedListener;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Adapter that wraps a {@link CacheEntryChangeListener} and implements
 * Hazelcast's entry listener interfaces.
 *
 * @param <T> the type of values in the map
 */
@RequiredArgsConstructor
public class CacheEntryChangeListenerAdapter<T>
        implements EntryAddedListener<String, T>,
        EntryUpdatedListener<String, T> {

    @Getter
    @NonNull
    private final CacheEntryChangeListener<T> delegate;

    @Getter
    private UUID registrationId;

    public void setRegistrationId(UUID id) {
        this.registrationId = id;
    }

    @Override
    public void entryAdded(EntryEvent<String, T> event) {
        if (event.getValue() != null) {
            delegate.onEntryChanged(event.getKey(), event.getValue());
        }
    }

    @Override
    public void entryUpdated(EntryEvent<String, T> event) {
        if (event.getValue() != null) {
            delegate.onEntryChanged(event.getKey(), event.getValue());
        }
    }
}
