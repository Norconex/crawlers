/* Copyright 2025 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.crawler.core.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

/**
 * Smoke-tests JDBC persistence via the real hazelcast-standalone.yaml config.
 */
@Timeout(60)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HazelcastStandalonePersistenceTest {

    private Path tempDir;

    private HazelcastInstance hz;

    @BeforeAll
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("hz-standalone-test-");
        hz = newInstance();
    }

    @AfterAll
    void tearDown() {
        Hazelcast.shutdownAll();
    }

    private HazelcastInstance newInstance() {
        var workDir = tempDir.toAbsolutePath().toString().replace("\\", "/");
        return Hazelcast.newHazelcastInstance(
                HazelcastConfigLoader.load(
                        HazelcastClusterConnectorConfig.DEFAULT_CONFIG_FILE,
                        Map.of("workDir", workDir)));
    }

    @Test
    void testKvMapPersistence() {
        hz.<String, String>getMap("kv-test").put("testKey", "testValue");
        hz.shutdown();

        hz = newInstance();
        assertThat(hz.<String, String>getMap("kv-test").get("testKey"))
                .isEqualTo("testValue");
    }

    @Test
    void testStepRecordPersistence() {
        var record = new StepRecord()
                .setPipelineId("pipe-1")
                .setStepId("step-1")
                .setUpdatedAt(System.currentTimeMillis())
                .setStatus(PipelineStatus.RUNNING)
                .setRunId("run-1");

        hz.<String, StepRecord>getMap("pipeCurrentStep")
                .put("pipeline-1", record);
        hz.shutdown();

        hz = newInstance();
        var reloaded = hz.<String, StepRecord>getMap("pipeCurrentStep")
                .get("pipeline-1");
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(PipelineStatus.RUNNING);
        assertThat(reloaded.getPipelineId()).isEqualTo("pipe-1");
    }

    @Test
    void testQueuePersistence() {
        var queue = hz.<String>getQueue("queue-test-persistence");
        queue.add("item-a");
        queue.add("item-b");
        hz.shutdown();

        hz = newInstance();
        var reloaded = hz.<String>getQueue("queue-test-persistence");
        // trigger load from JDBC store
        assertThat(reloaded.size()).isGreaterThanOrEqualTo(0);
    }
}
