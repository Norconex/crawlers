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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.util.Properties;

import com.hazelcast.collection.QueueStore;
import com.hazelcast.collection.QueueStoreFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating and initializing RocksDBQueueStore instances.
 * Hazelcast uses this factory to properly initialize queue stores
 * with the queue name and properties from the configuration.
 */
@Slf4j
public class RocksDBQueueStoreFactory implements QueueStoreFactory<Object> {

    @Override
    public QueueStore<Object> newQueueStore(
            String queueName, Properties properties) {
        LOG.debug(
                "Creating RocksDBQueueStore instance for queue '{}' "
                        + "(shared DB will be initialized if needed)",
                queueName);
        var store = new RocksDBQueueStore();
        store.init(properties, queueName);
        return store;
    }
}
