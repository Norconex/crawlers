package com.norconex.crawler.core.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MapStoreConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapStore;
import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.cluster.CacheMap;
import com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.TypedJdbcMapStoreFactory;

class IntegrationTypedFactoryTest {

    // simple POJO used as value type for the test
    public static class TestValue {
        public String s;

        public TestValue() {
        }

        public TestValue(String s) {
            this.s = s;
        }
    }

    @Test
    void testProgrammaticFactoryInstallCreatesTypedStore() throws Exception {
        String mapName = "itestMap";
        Config cfg = new Config();
        MapConfig mc = new MapConfig(mapName);
        MapStoreConfig msc = new MapStoreConfig();
        msc.setEnabled(true);
        // simulate YAML referencing the string-based store class
        msc.setClassName(
                "com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.StringJdbcMapStore");
        mc.setMapStoreConfig(msc);
        cfg.addMapConfig(mc);

        HazelcastInstance hz = Hazelcast.newHazelcastInstance(cfg);
        try {
            HazelcastCacheManager mgr = new HazelcastCacheManager(hz);
            // This should trigger MapStoreFactoryInstaller to replace class-name
            CacheMap<TestValue> cache =
                    mgr.getCacheMap(mapName, TestValue.class);
            assertThat(cache).isNotNull();

            MapStoreConfig installed = hz.getConfig()
                    .getMapConfig(mapName)
                    .getMapStoreConfig();
            assertThat(installed).isNotNull();
            Object factoryObj = installed.getFactoryImplementation();
            assertThat(factoryObj).isInstanceOf(TypedJdbcMapStoreFactory.class);
            TypedJdbcMapStoreFactory factory =
                    (TypedJdbcMapStoreFactory) factoryObj;
            assertThat(factory.getValueClass()).isEqualTo(TestValue.class);

            // Simulate Hazelcast invoking the factory to create a MapStore
            MapStore<String, Object> store =
                    factory.newMapStore(mapName, new Properties());
            assertThat(store).isNotNull();
            // The delegating MapStore should wrap a StringJdbcMapStore used
            // for actual persistence; verify that underlying instance exists.
            var fld = store.getClass().getDeclaredField("stringStore");
            fld.setAccessible(true);
            Object inner = fld.get(store);
            assertThat(inner).isNotNull();
            assertThat(inner.getClass().getName()).isEqualTo(
                    "com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.StringJdbcMapStore");

        } finally {
            hz.shutdown();
        }
    }
}
