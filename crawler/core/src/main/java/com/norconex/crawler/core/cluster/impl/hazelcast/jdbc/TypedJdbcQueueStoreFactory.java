/* Copyright 2026 Norconex Inc.
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


package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

import java.util.Properties;

import com.hazelcast.collection.QueueStore;
import com.hazelcast.collection.QueueStoreFactory;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.impl.hazelcast.LazyTypedStoreFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Factory to create a Hazelcast JDBC-backed QueueStore. This factory
 * requires a HazelcastInstance so created stores always obtain their
 * DataSource from the Hazelcast user context.
 */
@Slf4j
public class TypedJdbcQueueStoreFactory
        implements QueueStoreFactory<Object>, LazyTypedStoreFactory {

    private static final String PROP_VALUE_CLASS_NAME = "value-class-name";

    private transient HazelcastInstance hazelcastInstance;
    private String hazelcastInstanceName;
    private String valueClassName;

    public TypedJdbcQueueStoreFactory() {
        // hz will be set via setHazelcastInstance
    }

    @Override
    public void setValueClass(Class<?> valueClass) {
        this.valueClassName = valueClass == null ? null : valueClass.getName();
    }

    @Override
    public Class<?> getValueClass() {
        if (valueClassName == null) {
            return null;
        }
        try {
            return Class.forName(valueClassName);
        } catch (Exception e) {
            LOG.warn("Could not load valueClass '{}' from factory: {}",
                    valueClassName, e.getMessage());
            return null;
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hazelcastInstance = hz;
        this.hazelcastInstanceName = hz == null ? null : hz.getName();
    }

    @Override
    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    @SuppressWarnings("unchecked")
    @Override
    public QueueStore<Object> newQueueStore(
            String queueName, Properties properties) {
        // When Hazelcast creates this factory from the class name (rather than
        // a wired implementation), hazelcastInstanceName is not set yet.
        // Fall back to the name injected into store properties before startup.
        var effectiveInstanceName = hazelcastInstanceName != null
                ? hazelcastInstanceName
                : properties.getProperty("hz-instance-name");
        HazelcastInstance hz =
                com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastUtil
                        .resolveStoreInstance(
                                hazelcastInstance,
                                effectiveInstanceName,
                                queueName,
                                "queue");

        Class<?> vc = resolveValueClass(queueName, properties);
        return new TypedJdbcQueueStore<>(
                hz, queueName, properties, (Class<Object>) vc);
        //        return (QueueStore<Object>) (QueueStore<?>) new StringJdbcQueueStore(
        //                hz, queueName, properties);
    }

    private Class<?> resolveValueClass(
            String queueName, Properties properties) {
        if (properties != null) {
            var explicitType = properties.getProperty(PROP_VALUE_CLASS_NAME);
            if (explicitType != null && !explicitType.isBlank()) {
                try {
                    var cls = Class.forName(explicitType.trim());
                    LOG.debug(
                            "Loaded valueClass {} for queue '{}' from QueueStore property '{}'",
                            cls.getName(), queueName, PROP_VALUE_CLASS_NAME);
                    return cls;
                } catch (Exception e) {
                    LOG.warn(
                            "Invalid '{}' property '{}' for queue '{}'; falling back to String: {}",
                            PROP_VALUE_CLASS_NAME,
                            explicitType,
                            queueName,
                            e.getMessage());
                }
            }
        }

        if (valueClassName != null) {
            try {
                var cls = Class.forName(valueClassName);
                LOG.debug(
                        "Using injected valueClass {} for queue '{}'",
                        cls.getName(), queueName);
                return cls;
            } catch (Exception e) {
                LOG.warn(
                        "Invalid injected valueClass '{}' for queue '{}'; falling back: {}",
                        valueClassName, queueName, e.getMessage());
            }
        }

        LOG.warn(
                "valueClass not configured for queue '{}'; defaulting to String",
                queueName);
        return String.class;
    }
}
