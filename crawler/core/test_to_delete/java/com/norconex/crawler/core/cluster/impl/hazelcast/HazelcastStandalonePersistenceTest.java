package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.nio.file.Files;
import java.time.ZonedDateTime;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

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

        @BeforeAll
        void setup() throws Exception {
                var tempDir = Files.createTempDirectory("hz-standalone-test");
                // hazelcast-standalone.yaml contains ${workDir} in its JDBC URL;
                // set it as a system property so Hazelcast's ConfigReplacerHelper
                // can substitute it before the config is used.
                System.setProperty("workDir",
                                tempDir.toAbsolutePath().toString()
                                                .replace("\\", "/"));
                restartHazelcast();
        }

        private void restartHazelcast() throws Exception {
                if (hz != null) {
                        hz.shutdown();
                }
                hz = Hazelcast.newHazelcastInstance(
                                new YamlConfigBuilder(CONFIG_PATH).build());
        }

        @AfterAll
        void tearDown() {
                if (hz != null) {
                        hz.shutdown();
                }
                System.clearProperty("workDir");
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
                // Queue name must match the "queue-*" wildcard pattern in the
                // standalone YAML so that it gets a JDBC-backed queue store.
                var queue = hz.getQueue("queue-test");
                queue.add("testItem");
                restartHazelcast();
                var reloadedQueue = hz.getQueue("queue-test");
                reloadedQueue.size(); // Trigger loading from store
                Assertions.assertThat(reloadedQueue).contains("testItem");
        }
}
