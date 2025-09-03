package com.norconex.crawler.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core2.context.CrawlContext;

class CrawlSessionTest {

    private CrawlSession session;
    private Cache<String> cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCache<>();
        var cluster = mock(Cluster.class);
        var context = mock(CrawlContext.class);
        var cacheManager = mock(CacheManager.class);
        when(cluster.getCacheManager()).thenReturn(cacheManager);
        when(cacheManager.getCrawlSessionCache()).thenReturn(cache);
        when(cluster.getLocalNode()).thenReturn(null); // not used in test
        when(context.getId()).thenReturn("testCrawler");
        try {
            var ctor = CrawlSession.class
                    .getDeclaredConstructor(Cluster.class, CrawlContext.class);
            ctor.setAccessible(true);
            session = ctor.newInstance(cluster, context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // manually inject cache since we don't call init()
        try {
            var field =
                    CrawlSession.class.getDeclaredField("crawlSessionCache");
            field.setAccessible(true);
            field.set(session, cache);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testOncePerSessionRunsOnlyOnce() {
        var counter = new AtomicInteger();
        Runnable task = counter::incrementAndGet;
        session.oncePerSession("foo", task);
        session.oncePerSession("foo", task);
        session.oncePerSession("foo", task);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void testOncePerSessionAnGetRunsOnlyOnceAndReturnsValue() {
        var counter = new AtomicInteger();
        Supplier<String> supplier = () -> {
            counter.incrementAndGet();
            return "bar";
        };
        var v1 = session.oncePerSessionAndGet("bar", supplier);
        var v2 = session.oncePerSessionAndGet("bar", supplier);
        var v3 = session.oncePerSessionAndGet("bar", supplier);
        assertThat(counter.get()).isEqualTo(1);
        assertThat(v1).isEqualTo("bar");
        assertThat(v2).isEqualTo("bar");
        assertThat(v3).isEqualTo("bar");
    }

    @Test
    void oncePerSessionAndGet_handlesNonSerializableViaJsonWrapper() {
        var pojo = new NonSer("abc", 7);
        var out1 = session.oncePerSessionAndGet("nser", () -> pojo);
        assertThat(out1).isEqualTo(pojo);
        // subsequent calls must return cached value, not recompute
        var out2 = session.oncePerSessionAndGet("nser",
                () -> new NonSer("zzz", 9));
        assertThat(out2).isEqualTo(pojo);
    }

    @Test
    void oncePerSessionAndGet_handlesExplicitNull() {
        var out1 = session.oncePerSessionAndGet("null", () -> null);
        assertThat(out1).isNull();
        // cached null should be returned on subsequent calls
        var out2 = session.oncePerSessionAndGet("null", () -> "ignored");
        assertThat(out2).isNull();
    }

    // Simple in-memory cache for testing
    static class InMemoryCache<V> implements Cache<V> {
        private final java.util.Map<String, V> map = new java.util.HashMap<>();

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public void put(String key, V value) {
            map.put(key, value);
        }

        @Override
        public java.util.Optional<V> get(String key) {
            return java.util.Optional.ofNullable(map.get(key));
        }

        @Override
        public void remove(String key) {
            map.remove(key);
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public V computeIfAbsent(String key, java.util.function.Function<String,
                ? extends V> mappingFunction) {
            if (!map.containsKey(key)) {
                V value = mappingFunction.apply(key);
                map.put(key, value);
            }
            return map.get(key);
        }

        @Override
        public java.util.Optional<V> computeIfPresent(String key,
                java.util.function.BiFunction<String, ? super V,
                        ? extends V> remappingFunction) {
            if (map.containsKey(key)) {
                V newValue = remappingFunction.apply(key, map.get(key));
                map.put(key, newValue);
                return java.util.Optional.ofNullable(newValue);
            }
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<V> compute(String key,
                java.util.function.BiFunction<String, ? super V,
                        ? extends V> remappingFunction) {
            V newValue = remappingFunction.apply(key, map.get(key));
            map.put(key, newValue);
            return java.util.Optional.ofNullable(newValue);
        }

        @Override
        public V merge(String key, V value, java.util.function.BiFunction<
                ? super V, ? super V, ? extends V> remappingFunction) {
            var oldValue = map.get(key);
            var newValue = (oldValue == null) ? value
                    : remappingFunction.apply(oldValue, value);
            map.put(key, newValue);
            return newValue;
        }

        @Override
        public boolean containsKey(String key) {
            return map.containsKey(key);
        }

        @Override
        public V getOrDefault(String key, V defaultValue) {
            return map.getOrDefault(key, defaultValue);
        }

        @Override
        public V putIfAbsent(String key, V value) {
            var existing = map.get(key);
            if (existing == null) {
                map.put(key, value);
                return value;
            }
            return existing;
        }

        @Override
        public java.util.List<V> query(String queryExpression) {
            return java.util.Collections.emptyList();
        }

        @Override
        public boolean replace(String key, V oldValue, V newValue) {
            if (map.containsKey(key)
                    && java.util.Objects.equals(map.get(key), oldValue)) {
                map.put(key, newValue);
                return true;
            }
            return false;
        }

        @Override
        public java.util.Iterator<V> queryIterator(String queryExpression) {
            return java.util.Collections.emptyIterator();
        }

        @Override
        public java.util.List<V> queryPaged(String queryExpression,
                int startOffset, int maxResults) {
            return java.util.Collections.emptyList();
        }

        @Override
        public void queryStream(String queryExpression,
                java.util.function.Consumer<V> consumer, int batchSize) {
        }

        @Override
        public long count(String queryExpression) {
            return 0;
        }

        @Override
        public long size() {
            return map.size();
        }

        @Override
        public long delete(String queryExpression) {
            return 0;
        }

        @Override
        public void forEach(
                java.util.function.BiConsumer<String, ? super V> action) {
            map.forEach(action);
        }
    }

    // Non-serializable POJO but JSON-friendly for wrapper tests
    public static class NonSer {
        public String name;
        public int value;

        public NonSer() {
        }

        public NonSer(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof NonSer n))
                return false;
            return value == n.value && java.util.Objects.equals(name, n.name);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, value);
        }
    }
}
