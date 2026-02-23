package com.norconex.crawler.core.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.hazelcast.config.Config;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.CacheQueue;

class HazelcastQueueAdapterTest {

    private HazelcastInstance hz;

    @AfterEach
    void tearDown() {
        if (hz != null) {
            hz.shutdown();
        }
    }

    @Test
    void testStringPreserved() {
        Config cfg = new Config();
        cfg.addQueueConfig(new QueueConfig("test-queue"));
        hz = Hazelcast.newHazelcastInstance(cfg);

        try (var mgr = new HazelcastCacheManager(hz)) {
            CacheQueue<String> q =
                    mgr.getCacheQueue("test-queue", String.class);
            q.clear();
            q.add("alpha");
            String polled = q.poll();
            assertThat(polled).isEqualTo("alpha");
        }
    }

    public static class MyObj {
        public String name;
        public int x;

        public MyObj() {
        }

        public MyObj(String name, int x) {
            this.name = name;
            this.x = x;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            MyObj myObj = (MyObj) o;
            return x == myObj.x && (name == null ? myObj.name == null
                    : name.equals(myObj.name));
        }

        @Override
        public int hashCode() {
            int result = name == null ? 0 : name.hashCode();
            result = 31 * result + x;
            return result;
        }
    }

    @Test
    void testJsonSerializationRoundtrip() {
        Config cfg = new Config();
        cfg.addQueueConfig(new QueueConfig("obj-queue"));
        hz = Hazelcast.newHazelcastInstance(cfg);

        try (var mgr = new HazelcastCacheManager(hz)) {
            CacheQueue<MyObj> q = mgr.getCacheQueue("obj-queue", MyObj.class);

            q.clear();
            var obj = new MyObj("bob", 42);
            q.add(obj);
            MyObj polled = q.poll();
            assertThat(polled).isEqualTo(obj);
        }
    }
}
