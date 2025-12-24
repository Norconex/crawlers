package com.norconex.crawler.core.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.Test;

class HazelcastCacheManagerTest {

    @Test
    void testGetCacheInstallsFactory() {
        HazelcastInstance hz = mock(HazelcastInstance.class);
        Config cfg = new Config();
        when(hz.getConfig()).thenReturn(cfg);

        // Mock lifecycle so isRunning() returns true (avoids NPE)
        LifecycleService lifecycle = mock(LifecycleService.class);
        when(lifecycle.isRunning()).thenReturn(true);
        when(hz.getLifecycleService()).thenReturn(lifecycle);

        // Mock map returned by Hazelcast instance
        @SuppressWarnings("unchecked")
        IMap<Object, Object> imap = mock(IMap.class);
        when(hz.getMap("testMap")).thenReturn(imap);

        HazelcastCacheManager mgr = new HazelcastCacheManager(hz);
        var cache = mgr.getCacheMap("testMap", String.class);
        assertThat(cache).isNotNull();
    }
}
