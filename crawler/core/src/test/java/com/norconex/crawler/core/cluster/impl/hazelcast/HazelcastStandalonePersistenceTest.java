package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.hazelcast.config.Config;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.ledger.ProcessingStatus;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HazelcastStandalonePersistenceTest {
    private HazelcastInstance hz;
    private static final String CONFIG_PATH =
            "src/main/resources/cache/hazelcast-standalone.yaml";
    private static final String JDBC_URL_PREFIX = "jdbc:h2:";
    private String jdbcUrl;

    @BeforeAll
    void setup() throws Exception {
        var tempDir = Files.createTempDirectory("hz-standalone-test");
        jdbcUrl = JDBC_URL_PREFIX
                + tempDir.resolve("crawler_db").toAbsolutePath();
        restartHazelcast();
    }

    private void configureJdbcStores(Config config, String jdbcUrl) {
        List.of(new MapTable("crawler", "HZ_MAP_CRAWLER"),
                new MapTable("ledger_a", "HZ_MAP_LEDGER_A"),
                new MapTable("ledger_b", "HZ_MAP_LEDGER_B"),
                new MapTable("pipeCurrentStep", "HZ_MAP_PIPE_CURRENT_STEP"),
                new MapTable("pipeWorkerStatuses",
                        "HZ_MAP_PIPE_WORKER_STATUSES"))
                .forEach(mt -> applyMapStore(config, mt.name(),
                        mt.table(), jdbcUrl));

        var queueConfig = config.getQueueConfig("crawlQueue");
        var queueStore = queueConfig.getQueueStoreConfig();
        queueStore.setEnabled(true);
        queueStore.setFactoryClassName(
                "com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.HazelcastJdbcQueueStoreFactory");
        setJdbcProperties(queueStore.getProperties(), jdbcUrl,
                "HZ_JDBC_QUEUE");
        queueStore.getProperties().setProperty("store.orderColumn",
                "QUEUE_ORDER");
        queueStore.getProperties().setProperty("store.valueColumn",
                "QUEUE_VALUE");
    }

    private void applyMapStore(Config config, String mapName,
            String table, String jdbcUrl) {
        var mapConfig = config.getMapConfig(mapName);
        mapConfig.setBackupCount(0);
        var storeConfig = mapConfig.getMapStoreConfig();
        storeConfig.setEnabled(true);
        storeConfig.setClassName(
                "com.norconex.crawler.core.cluster.impl.hazelcast.jdbc.HazelcastJdbcMapStore");
        setJdbcProperties(storeConfig.getProperties(), jdbcUrl, table);
        storeConfig.getProperties().setProperty("store.keyColumn",
                "MAP_KEY");
        storeConfig.getProperties().setProperty("store.valueColumn",
                "MAP_VALUE");
    }

    private void setJdbcProperties(java.util.Properties props,
            String jdbcUrl, String table) {
        props.setProperty("jdbc.url", jdbcUrl);
        props.setProperty("jdbc.username", "sa");
        props.setProperty("jdbc.password", "");
        props.setProperty("store.table", table);
    }

    private void restartHazelcast() throws IOException {
        if (hz != null) {
            hz.shutdown();
        }
        var config = new YamlConfigBuilder(CONFIG_PATH).build();
        configureJdbcStores(config, jdbcUrl);
        //        HazelcastDatabaseMigrator.migrateH2(jdbcUrl);
        hz = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    void tearDown() {
        if (hz != null) {
            hz.shutdown();
        }
    }

    @Test
    void testSimpleKeyValuePersistence() throws Exception {
        IMap<String, String> crawlerMap = hz.getMap("crawler");
        crawlerMap.put("testKey", "testValue");
        restartHazelcast();
        IMap<String, String> crawlerMapReloaded = hz.getMap("crawler");
        Assertions.assertThat(crawlerMapReloaded.get("testKey"))
                .isEqualTo("testValue");
    }

    @Test
    void testLedgerAPersistence() throws Exception {
        var ledgerA = hz.<String, CrawlEntry>getMap("ledger_a");
        var entry = new CrawlEntry("ref-123");
        entry.setDepth(1);
        entry.setProcessingStatus(ProcessingStatus.QUEUED);
        entry.setProcessingOutcome(ProcessingOutcome.NEW);
        entry.addToReferenceTrail("trail");
        entry.setMetaChecksum("meta");
        entry.setContentChecksum("content");
        entry.setQueuedAt(ZonedDateTime.now());
        entry.setOrphan(false);
        entry.setDeleted(false);
        ledgerA.put(entry.getReference(), entry);
        restartHazelcast();
        var ledgerAReloaded = hz.<String, CrawlEntry>getMap("ledger_a");
        var reloadedEntry = ledgerAReloaded.get(entry.getReference());
        Assertions.assertThat(reloadedEntry).isNotNull();
        Assertions.assertThat(reloadedEntry.getProcessingStatus())
                .isEqualTo(ProcessingStatus.QUEUED);
    }

    @Test
    void testPipeCurrentStepPersistence() throws Exception {
        var stepMap = hz.<String, StepRecord>getMap("pipeCurrentStep");
        var record = new StepRecord()
                .setPipelineId("id-1")
                .setStepId("step-1")
                .setUpdatedAt(System.currentTimeMillis())
                .setStatus(PipelineStatus.RUNNING)
                .setRunId("run-1");
        stepMap.put("pipeline-1", record);
        restartHazelcast();
        var reloaded = hz.<String, StepRecord>getMap("pipeCurrentStep")
                .get("pipeline-1");
        Assertions.assertThat(reloaded).isNotNull();
        Assertions.assertThat(reloaded.getStatus())
                .isEqualTo(PipelineStatus.RUNNING);
    }

    @Test
    void testQueuePersistence() throws Exception {
        var queue = hz.getQueue("crawlQueue");
        queue.add("testItem");
        restartHazelcast();
        var reloadedQueue = hz.getQueue("crawlQueue");
        reloadedQueue.size(); // Trigger loading from store
        Assertions.assertThat(reloadedQueue).contains("testItem");
    }

    private record MapTable(String name, String table) {
    }
}
