package com.norconex.crawler.core.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HazelcastStandaloneConfigPersistenceTest {
    private HazelcastInstance hz;
    private static final String CONFIG_PATH =
            "src/main/resources/cache/hazelcast-standalone.yaml";
    private static final String DB_PATH = "./data/crawler_db.mv.db";

    @BeforeAll
    void setup() throws java.io.FileNotFoundException {
        // Clean up previous DB file for a fresh test
        var dbFile = new File(DB_PATH);
        if (dbFile.exists())
            dbFile.delete();
        var config = new YamlConfigBuilder(CONFIG_PATH).build();
        hz = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    void teardown() {
        if (hz != null)
            hz.shutdown();
    }

    @Test
    void testSimpleKeyValuePersistence() throws java.io.FileNotFoundException {
        IMap<String, String> crawlerMap = hz.getMap("crawler");
        crawlerMap.put("testKey", "testValue");
        hz.shutdown();
        // Restart Hazelcast to test persistence
        var config = new YamlConfigBuilder(CONFIG_PATH).build();
        hz = Hazelcast.newHazelcastInstance(config);
        IMap<String, String> crawlerMap2 = hz.getMap("crawler");
        assertThat(crawlerMap2.get("testKey")).isEqualTo("testValue");
    }

    @Test
    void testLedgerAPersistence() throws java.io.FileNotFoundException {
        IMap<String, Map<String, Object>> ledgerA = hz.getMap("ledger_a");
        var ref = "ref-123";
        java.util.Map<String, Object> entry = new java.util.HashMap<>();
        entry.put("depth", 1);
        entry.put("processingStatus", "QUEUED");
        entry.put("processingOutcome", "SUCCESS");
        entry.put("referenceTrail", "trail");
        entry.put("metaChecksum", "meta");
        entry.put("contentChecksum", "content");
        entry.put("queuedAt", System.currentTimeMillis());
        entry.put("processingAt", null);
        entry.put("processedAt", null);
        entry.put("orphan", false);
        entry.put("deleted", false);
        ledgerA.put(ref, entry);
        hz.shutdown();
        var config = new YamlConfigBuilder(CONFIG_PATH).build();
        hz = Hazelcast.newHazelcastInstance(config);
        IMap<String, Map<String, Object>> ledgerA2 = hz.getMap("ledger_a");
        var loaded = ledgerA2.get(ref);
        assertThat(loaded).isNotNull();
        assertThat(loaded.get("processingStatus")).isEqualTo("QUEUED");
    }

    @Test
    void testPipeCurrentStepPersistence() throws java.io.FileNotFoundException {
        IMap<String, Map<String, Object>> pipeCurrentStep =
                hz.getMap("pipeCurrentStep");
        var pipelineKey = "pipe-1";
        java.util.Map<String, Object> entry = new java.util.HashMap<>();
        entry.put("pipelineId", "pipeline-xyz");
        entry.put("stepId", "step-abc");
        entry.put("updatedAt", System.currentTimeMillis());
        entry.put("status", "RUNNING");
        entry.put("runId", "run-001");
        pipeCurrentStep.put(pipelineKey, entry);
        hz.shutdown();
        var config = new YamlConfigBuilder(CONFIG_PATH).build();
        hz = Hazelcast.newHazelcastInstance(config);
        IMap<String, Map<String, Object>> pipeCurrentStep2 =
                hz.getMap("pipeCurrentStep");
        var loaded = pipeCurrentStep2.get(pipelineKey);
        assertThat(loaded).isNotNull();
        assertThat(loaded.get("status")).isEqualTo("RUNNING");
    }
}
