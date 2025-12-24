package com.norconex.crawler.core.cluster.impl.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.QueueConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.CacheQueue;

public class QueueAdapterSmokeRunner {
    public static void main(String[] args) {
        Config cfg = new Config();
        cfg.addQueueConfig(new QueueConfig("smoke-queue"));
        cfg.addQueueConfig(new QueueConfig("smoke-obj-queue"));
        HazelcastInstance hz = Hazelcast.newHazelcastInstance(cfg);
        try {
            HazelcastCacheManager mgr = new HazelcastCacheManager(hz);

            // String test
            CacheQueue<String> q1 =
                    mgr.getCacheQueue("smoke-queue", String.class);
            q1.clear();
            q1.add("hello");
            String p = q1.poll();
            if (!"hello".equals(p)) {
                System.err.println("STRING TEST FAILED: got=" + p);
                System.exit(2);
            }

            // Object test
            CacheQueue<Pojo> q2 =
                    mgr.getCacheQueue("smoke-obj-queue", Pojo.class);
            q2.clear();
            Pojo o = new Pojo("alice", 7);
            q2.add(o);
            Pojo r = q2.poll();
            if (!o.equals(r)) {
                System.err.println("OBJ TEST FAILED: got=" + r);
                System.exit(3);
            }

            System.out.println("SMOKE PASS");
        } finally {
            hz.shutdown();
        }
    }

    public static class Pojo {
        public String name;
        public int v;

        public Pojo() {
        }

        public Pojo(String name, int v) {
            this.name = name;
            this.v = v;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Pojo p = (Pojo) o;
            if (v != p.v)
                return false;
            return name == null ? p.name == null : name.equals(p.name);
        }

        @Override
        public String toString() {
            return "Pojo{" + name + "," + v + "}";
        }
    }
}
