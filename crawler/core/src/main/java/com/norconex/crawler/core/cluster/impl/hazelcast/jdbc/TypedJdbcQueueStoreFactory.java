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

    private HazelcastInstance hz;
    private Class<?> valueClass;

    public TypedJdbcQueueStoreFactory() {
        // hz will be set via setHazelcastInstance
    }

    @Override
    public void setValueClass(Class<?> valueClass) {
        this.valueClass = valueClass;
    }

    @Override
    public Class<?> getValueClass() {
        return valueClass;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }

    @Override
    public HazelcastInstance getHazelcastInstance() {
        return hz;
    }

    @SuppressWarnings("unchecked")
    @Override
    public QueueStore<Object> newQueueStore(
            String queueName, Properties properties) {

        if (valueClass == null) {
            if ("__cache_types".equals(queueName)) {
                LOG.debug("Defaulting valueClass to String for queue '{}'",
                        queueName);
            } else {
                LOG.warn("valueClass not set on TypedJdbcQueueStoreFactory "
                        + "for queue '{}'; defaulting to String", queueName);
            }
            valueClass = String.class;
        }
        return new TypedJdbcQueueStore<>(
                hz, queueName, properties, (Class<Object>) valueClass);
        //        return (QueueStore<Object>) (QueueStore<?>) new StringJdbcQueueStore(
        //                hz, queueName, properties);
    }
}
