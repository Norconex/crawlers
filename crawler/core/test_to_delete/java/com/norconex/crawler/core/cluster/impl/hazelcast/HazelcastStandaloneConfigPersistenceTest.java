package com.norconex.crawler.core.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.ledger.ProcessingStatus;

/**
 * Smoke-tests persistence using the real hazelcast-standalone.yaml config.
 * The {@code ${workDir}} placeholder in the YAML is resolved via a system
 * property set before loading the config.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HazelcastStandaloneConfigPersistenceTest {

    private static final String CONFIG_PATH =
            "src/main/resources/cache/hazelcast-standalone.yaml";

    @TempDir
    Path tempDir;

    private HazelcastInstance hz;

    @BeforeAll
    void setup() throws Exception {
        // hazelcast-standalone.yaml contains jdbc:h2:file:${workDir}/db so
        // Hazelcast's ConfigReplacerHelper can substitute the path from here.
        System.setProperty("workDir",
                tempDir.toAbsolutePath().toString().replace("\\", "/"));
        hz = newInstance();
    }

    @AfterAll
    void teardown() {
        if (hz != null) {
            hz.shutdown();
        }
        System.clearProperty("workDir");
    }

    private HazelcastInstance newInstance() throws Exception {
        return Hazelcast.newHazelcastInstance(
                new YamlConfigBuilder(CONFIG_PATH).build());
    }

    @Test
    void testSimpleKeyValuePersistence() throws Exception {
        hz.<String, String>getMap("crawler").put("testKey", "testValue");
        hz.shutdown();
        hz = newInstance();
        assertThat(hz.<String, String>getMap("crawler").get("testKey"))
                .isEqualTo("testValue");
    }

    @Test
    void testLedgerAPersistence() throws Exception {
        var entry = new CrawlEntry("ref-123");
        entry.setDepth(1);
        entry.setProcessingStatus(ProcessingStatus.QUEUED);
        entry.setProcessingOutcome(ProcessingOutcome.NEW);
        entry.addToReferenceTrail("trail");
        entry.setMetaChecksum("meta");
        entry.setContentChecksum("content");
        entry.setQueuedAt(ZonedDateTime.now());
        hz.<String, CrawlEntry>getMap("ledger_a").put(entry.getReference(),
                entry);
        hz.shutdown();
        hz = newInstance();
        var loaded = hz.<String, CrawlEntry>getMap("ledger_a").get("ref-123");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getProcessingStatus())
                .isEqualTo(ProcessingStatus.QUEUED);
    }

    @Test
    void testPipeCurrentStepPersistence() throws Exception {
        var record = new StepRecord()
                .setPipelineId("pipeline-xyz")
                .setStepId("step-abc")
                .setUpdatedAt(System.currentTimeMillis())
                .setStatus(PipelineStatus.RUNNING)
                .setRunId("run-001");
        hz.<String, StepRecord>getMap("pipeCurrentStep").put("pipe-1", record);
        hz.shutdown();
        hz = newInstance();
        var loaded =
                hz.<String, StepRecord>getMap("pipeCurrentStep").get("pipe-1");
        assertThat(loaded).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(PipelineStatus.RUNNING);
    }
}
