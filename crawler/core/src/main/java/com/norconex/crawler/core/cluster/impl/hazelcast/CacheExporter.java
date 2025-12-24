package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.apache.commons.lang3.SerializationException;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.SerializedCache;
import com.norconex.crawler.core.cluster.SerializedCache.CacheType;
import com.norconex.crawler.core.cluster.SerializedCache.SerializedEntry;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
class CacheExporter {

    static void export(HazelcastCacheManager manager,
            Consumer<SerializedCache> c) {
        var cacheTypes = manager.getCacheTypes();
        var hazelcast = manager.getHazelcastInstance();

        getCacheObjects(hazelcast).forEach(obj -> {
            var serialCache = createSerializedCache(obj, cacheTypes, hazelcast);
            c.accept(serialCache);
        });
    }

    private static List<DistributedObject>
            getCacheObjects(HazelcastInstance hazelcast) {
        return hazelcast.getDistributedObjects().stream()
                .filter(HazelcastUtil::isSupportedCacheType)
                .filter(obj -> !HazelcastCacheManager.TYPE_REGISTRY_MAP
                        .equals(obj.getName()))
                .toList();
    }

    private static SerializedCache createSerializedCache(DistributedObject obj,
            com.norconex.crawler.core.cluster.CacheMap<String> cacheTypes,
            HazelcastInstance hazelcast) {
        var serialCache = new SerializedCache();
        serialCache.setPersistent(HazelcastUtil.isPersistent(
                hazelcast, obj.getName()));
        serialCache.setCacheName(obj.getName());
        serialCache.setClassName(
                cacheTypes.get(obj.getName()).orElse(null));

        if (obj instanceof IMap) {
            @SuppressWarnings("unchecked")
            IMap<String, ?> imap = (IMap<String, ?>) obj;
            serialCache.setCacheType(CacheType.MAP);
            serialCache.setEntries(createMapIterator(imap));
        } else if (obj instanceof IQueue) {
            IQueue<?> iqueue = (IQueue<?>) obj;
            serialCache.setCacheType(CacheType.QUEUE);
            serialCache.setEntries(createQueueIterator(iqueue));
        }

        return serialCache;
    }

    private static Iterator<SerializedEntry>
            createMapIterator(IMap<String, ?> imap) {
        var rawIt = imap.iterator();

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return rawIt.hasNext();
            }

            @Override
            public SerializedEntry next() {
                Entry<String, ?> e = rawIt.next();
                var key = e.getKey() == null ? null : e.getKey();
                Object val = e.getValue();
                if (val == null) {
                    return new SerializedEntry(key, null);
                }
                if (val instanceof String strVal) {
                    return new SerializedEntry(key, strVal);
                }
                // serialize any non-String value to JSON
                try {
                    var json = SerialUtil.toJsonString(val);
                    return new SerializedEntry(key, json);
                } catch (SerializationException ex) {
                    LOG.debug("Could not serialize value for cache '{}': {}",
                            imap.getName(), ex.toString());
                    return new SerializedEntry(key, val.toString());
                }
            }
        };
    }

    private static Iterator<SerializedEntry>
            createQueueIterator(IQueue<?> iqueue) {
        var rawIt = iqueue.iterator();

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return rawIt.hasNext();
            }

            @Override
            public SerializedEntry next() {
                Object val = rawIt.next();
                if (val == null) {
                    return new SerializedEntry(null, null);
                }
                if (val instanceof String strVal) {
                    return new SerializedEntry(null, strVal);
                }
                // serialize any non-String value to JSON
                try {
                    var json = SerialUtil.toJsonString(val);
                    return new SerializedEntry(null, json);
                } catch (SerializationException ex) {
                    LOG.debug("Could not serialize value for cache '{}': {}",
                            iqueue.getName(), ex.toString());
                    return new SerializedEntry(null, val.toString());
                }
            }
        };
    }
}