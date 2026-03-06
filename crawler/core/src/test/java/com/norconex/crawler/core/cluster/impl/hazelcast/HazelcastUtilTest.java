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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

@Timeout(30)
class HazelcastUtilTest {

    private HazelcastInstance hz;

    @AfterEach
    void tearDown() {
        Hazelcast.shutdownAll();
    }

    // -----------------------------------------------------------------
    // isSupportedCacheType
    // -----------------------------------------------------------------

    @Test
    void testIsSupportedCacheType_withIMap() {
        hz = HazelcastTestSupport.startNode();
        IMap<String, String> map = hz.getMap("test-map");
        assertThat(HazelcastUtil.isSupportedCacheType(map)).isTrue();
    }

    @Test
    void testIsSupportedCacheType_withIQueue() {
        hz = HazelcastTestSupport.startNode();
        IQueue<String> queue = hz.getQueue("test-queue");
        assertThat(HazelcastUtil.isSupportedCacheType(queue)).isTrue();
    }

    @Test
    void testIsSupportedCacheType_withRandomObject() {
        assertThat(HazelcastUtil.isSupportedCacheType("string")).isFalse();
        assertThat(HazelcastUtil.isSupportedCacheType(42)).isFalse();
        assertThat(HazelcastUtil.isSupportedCacheType(null)).isFalse();
    }

    // -----------------------------------------------------------------
    // isPersistent
    // -----------------------------------------------------------------

    @Test
    void testIsPersistent_noMapStoreReturnsfalse() {
        hz = HazelcastTestSupport.startNode();
        // By default no store configured → should return false
        assertThat(HazelcastUtil.isPersistent(hz, "any-map")).isFalse();
    }

    @Test
    void testIsPersistent_withJdbcMapStore_returnsTrue() throws IOException {
        var workDir = Files.createTempDirectory("hz-persist-map-test-");
        hz = Hazelcast.newHazelcastInstance(
                new JdbcHazelcastConfigurer().buildConfig(
                        new HazelcastConfigurerContext(
                                workDir, false, "test-cluster")));
        // default map config has MapStore enabled → isPersistent returns true
        assertThat(HazelcastUtil.isPersistent(hz, "any-named-map")).isTrue();
    }

    @Test
    void testIsPersistent_withJdbcQueueStore_returnsTrue() throws IOException {
        var workDir = Files.createTempDirectory("hz-persist-queue-test-");
        hz = Hazelcast.newHazelcastInstance(
                new JdbcHazelcastConfigurer().buildConfig(
                        new HazelcastConfigurerContext(
                                workDir, false, "test-cluster")));
        // queue-* pattern matches queue store config → isPersistent returns true
        assertThat(HazelcastUtil.isPersistent(hz, "queue-test")).isTrue();
    }

    // -----------------------------------------------------------------
    // isPipelineTerminated
    // -----------------------------------------------------------------

    @Test
    void testIsPipelineTerminated_nullStepRecordReturnsFalse() {
        var pipeline = buildSingleStepPipeline();
        assertThat(HazelcastUtil.isPipelineTerminated(
                pipeline, null, false)).isFalse();
    }

    @Test
    void testIsPipelineTerminated_nullStatusReturnsFalse() {
        var pipeline = buildSingleStepPipeline();
        var rec = new StepRecord().setStepId("step1").setPipelineId("p1");
        assertThat(HazelcastUtil.isPipelineTerminated(
                pipeline, rec, false)).isFalse();
    }

    @Test
    void testIsPipelineTerminated_nonTerminalStatusReturnsFalse() {
        var pipeline = buildSingleStepPipeline();
        var rec = new StepRecord()
                .setStepId("step1")
                .setPipelineId("p1")
                .setStatus(PipelineStatus.RUNNING);
        assertThat(HazelcastUtil.isPipelineTerminated(
                pipeline, rec, false)).isFalse();
    }

    @Test
    void testIsPipelineTerminated_completedLastStep() {
        var pipeline = buildSingleStepPipeline();
        var rec = new StepRecord()
                .setStepId("step1")
                .setPipelineId("p1")
                .setStatus(PipelineStatus.COMPLETED);
        assertThat(HazelcastUtil.isPipelineTerminated(
                pipeline, rec, false)).isTrue();
    }

    @Test
    void testIsPipelineTerminated_stoppedNotLastStep() {
        var pipeline = buildTwoStepPipeline();
        var rec = new StepRecord()
                .setStepId("step1") // first step, not last
                .setPipelineId("p1")
                .setStatus(PipelineStatus.COMPLETED);
        // stop requested → terminated even if not last step
        assertThat(HazelcastUtil.isPipelineTerminated(
                pipeline, rec, true)).isTrue();
    }

    @Test
    void testIsPipelineTerminated_failedNonCompletedTerminalStatus() {
        var pipeline = buildSingleStepPipeline();
        var rec = new StepRecord()
                .setStepId("step1")
                .setPipelineId("p1")
                .setStatus(PipelineStatus.FAILED);
        assertThat(HazelcastUtil.isPipelineTerminated(
                pipeline, rec, false)).isTrue();
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Pipeline buildSingleStepPipeline() {
        var step = mock(Step.class);
        when(step.getId()).thenReturn("step1");
        return new Pipeline("p1", java.util.List.of(step));
    }

    private Pipeline buildTwoStepPipeline() {
        var step1 = mock(Step.class);
        when(step1.getId()).thenReturn("step1");
        var step2 = mock(Step.class);
        when(step2.getId()).thenReturn("step2");
        return new Pipeline("p1", java.util.List.of(step1, step2));
    }
}
