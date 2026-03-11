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

    private transient HazelcastInstance hazelcastInstance;
    private String hazelcastInstanceName;
    private String valueClassName;

    private static final String TYPE_REGISTRY_MAP = "__cache_types";
    private static final long TYPE_LOOKUP_TIMEOUT_MS = 2_000;
    private static final long TYPE_LOOKUP_POLL_MS = 50;

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

        Class<?> vc = resolveValueClass(hz, queueName);
        return new TypedJdbcQueueStore<>(
                hz, queueName, properties, (Class<Object>) vc);
        //        return (QueueStore<Object>) (QueueStore<?>) new StringJdbcQueueStore(
        //                hz, queueName, properties);
    }

    private Class<?> resolveValueClass(HazelcastInstance hz, String queueName) {
        String typeName = null;
        try {
            var typeRegistry = hz.getMap(TYPE_REGISTRY_MAP);
            var deadline = System.currentTimeMillis() + TYPE_LOOKUP_TIMEOUT_MS;
            while (typeName == null && System.currentTimeMillis() <= deadline) {
                typeName = (String) typeRegistry.get(queueName);
                if (typeName != null) {
                    break;
                }
                try {
                    Thread.sleep(TYPE_LOOKUP_POLL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not read type registry for queue '{}': {}",
                    queueName, e.toString());
        }

        if (typeName != null) {
            try {
                var cls = Class.forName(typeName);
                LOG.debug(
                        "Loaded valueClass {} for queue '{}' from type registry",
                        cls.getName(), queueName);
                return cls;
            } catch (Exception e) {
                LOG.warn(
                        "Invalid valueClass '{}' in type registry for queue '{}'; falling back: {}",
                        typeName, queueName, e.getMessage());
            }
        }

        if (valueClassName != null) {
            try {
                var cls = Class.forName(valueClassName);
                LOG.debug(
                        "Using injected valueClass {} for queue '{}' (type registry missing)",
                        cls.getName(), queueName);
                return cls;
            } catch (Exception e) {
                LOG.warn(
                        "Invalid injected valueClass '{}' for queue '{}'; falling back: {}",
                        valueClassName, queueName, e.getMessage());
            }
        }

        LOG.debug("Defaulting valueClass to String for queue '{}'", queueName);
        return String.class;
    }
}
