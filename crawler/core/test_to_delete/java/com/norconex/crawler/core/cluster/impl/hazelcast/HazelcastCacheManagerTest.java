package com.norconex.crawler.core.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import com.hazelcast.map.IMap;

class HazelcastCacheManagerTest {

    @Test
    void testGetCacheInstallsFactory() {
        HazelcastInstance hz = mock(HazelcastInstance.class);
        Config cfg = new Config();
        when(hz.getConfig()).thenReturn(cfg);
        when(hz.getName()).thenReturn("test-hz-instance");

        // Mock lifecycle so isRunning() returns true (avoids NPE)
        LifecycleService lifecycle = mock(LifecycleService.class);
        when(lifecycle.isRunning()).thenReturn(true);
        when(hz.getLifecycleService()).thenReturn(lifecycle);

        // Mock map returned by Hazelcast instance
        @SuppressWarnings("unchecked")
        IMap<Object, Object> imap = mock(IMap.class);
        when(hz.getMap("testMap")).thenReturn(imap);

        try (var mgr = new HazelcastCacheManager(hz)) {
            var cache = mgr.getCacheMap("testMap", String.class);
            assertThat(cache).isNotNull();
        }
    }
}
