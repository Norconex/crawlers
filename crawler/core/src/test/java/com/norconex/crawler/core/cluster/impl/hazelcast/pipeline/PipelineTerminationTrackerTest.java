/* Copyright 2026 Norconex Inc.
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
package com.norconex.crawler.core.cluster.impl.hazelcast.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.LifecycleService;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastCluster;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterNode;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

@Timeout(30)
class PipelineTerminationTrackerTest {

    @Test
    void await_returnsCompletedWhenCurrentStepIsTerminal() {
        var record = new StepRecord()
                .setPipelineId("pipe-1")
                .setStepId("step-1")
                .setStatus(PipelineStatus.COMPLETED)
                .setUpdatedAt(System.currentTimeMillis());
        var tracker = new PipelineTerminationTracker(
                cluster(true), pipeline("step-1"), state(record, false, 123L));

        var result = tracker.await(5_000);

        assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
        assertThat(result.isTimedOut()).isFalse();
        assertThat(result.getLastStepId()).isEqualTo("step-1");
    }

    @Test
    void await_marksExpiredWhenHeartbeatTimesOut() {
        var record = new StepRecord()
                .setPipelineId("pipe-1")
                .setStepId("step-1")
                .setStatus(PipelineStatus.RUNNING)
                .setUpdatedAt(System.currentTimeMillis() - 5_000);
        var tracker = new PipelineTerminationTracker(
                cluster(true), pipeline("step-1"), state(record, false, 123L));

        var result = tracker.await(100);

        assertThat(result.getStatus()).isEqualTo(PipelineStatus.EXPIRED);
        assertThat(result.isTimedOut()).isTrue();
    }

    @Test
    void await_marksFailedWhenClusterIsNotRunning() {
        var record = new StepRecord()
                .setPipelineId("pipe-1")
                .setStepId("step-1")
                .setStatus(PipelineStatus.RUNNING)
                .setUpdatedAt(System.currentTimeMillis());
        var tracker = new PipelineTerminationTracker(
                cluster(false), pipeline("step-1"), state(record, false, 123L));

        var result = tracker.await(5_000);

        assertThat(result.getStatus()).isEqualTo(PipelineStatus.FAILED);
        assertThat(result.isTimedOut()).isFalse();
    }

    @Test
    void await_marksFailedWhenCurrentThreadIsInterrupted() {
        var record = new StepRecord()
                .setPipelineId("pipe-1")
                .setStepId("step-1")
                .setStatus(PipelineStatus.RUNNING)
                .setUpdatedAt(System.currentTimeMillis());
        var tracker = new PipelineTerminationTracker(
                cluster(true), pipeline("step-1"), state(record, false, 123L));

        try {
            Thread.currentThread().interrupt();

            var result = tracker.await(5_000);

            assertThat(result.getStatus()).isEqualTo(PipelineStatus.FAILED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void await_returnsCompletedWhenStopWasRequestedAfterTerminalStep() {
        var record = new StepRecord()
                .setPipelineId("pipe-1")
                .setStepId("step-1")
                .setStatus(PipelineStatus.COMPLETED)
                .setUpdatedAt(System.currentTimeMillis());
        var tracker = new PipelineTerminationTracker(
                cluster(true), pipeline("step-1", "step-2"),
                state(record, true, 123L));

        var result = tracker.await(5_000);

        assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
        assertThat(result.getLastStepId()).isEqualTo("step-1");
    }

    @Test
    void await_returnsFailedForTerminalNonCompletedIntermediateStep() {
        var record = new StepRecord()
                .setPipelineId("pipe-1")
                .setStepId("step-1")
                .setStatus(PipelineStatus.FAILED)
                .setUpdatedAt(System.currentTimeMillis());
        var tracker = new PipelineTerminationTracker(
                cluster(true), pipeline("step-1", "step-2"),
                state(record, false, 123L));

        var result = tracker.await(5_000);

        assertThat(result.getStatus()).isEqualTo(PipelineStatus.FAILED);
        assertThat(result.isTimedOut()).isFalse();
    }

    @Test
    void await_handlesStaleTerminalRecordThenExpires() {
        var record = new StepRecord()
                .setPipelineId("pipe-1")
                .setStepId("step-1")
                .setStatus(PipelineStatus.COMPLETED)
                .setUpdatedAt(100L);
        var tracker = new PipelineTerminationTracker(
                cluster(true), pipeline("step-1", "step-2"),
                state(record, false, 200L));

        var result = tracker.await(1);

        assertThat(result.getStatus()).isEqualTo(PipelineStatus.COMPLETED);
        assertThat(result.isTimedOut()).isTrue();
    }

    @Test
    void await_marksFailedWhenInterruptedDuringSleep() throws Exception {
        var record = new StepRecord()
                .setPipelineId("pipe-1")
                .setStepId("step-1")
                .setStatus(PipelineStatus.RUNNING)
                .setUpdatedAt(System.currentTimeMillis());
        var tracker = new PipelineTerminationTracker(
                cluster(true), pipeline("step-1", "step-2"),
                state(record, false, 123L));
        var started = new CountDownLatch(1);
        var resultRef = new AtomicReference<
                com.norconex.crawler.core.cluster.pipeline.PipelineResult>();

        var thread = new Thread(() -> {
            started.countDown();
            resultRef.set(tracker.await(0));
        }, "tracker-await-test");

        thread.start();
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(150);
        thread.interrupt();
        thread.join(2_000);

        assertThat(thread.isAlive()).isFalse();
        assertThat(resultRef.get()).isNotNull();
        assertThat(resultRef.get().getStatus())
                .isEqualTo(PipelineStatus.FAILED);
        assertThat(resultRef.get().isTimedOut()).isFalse();
    }

    private HazelcastCluster cluster(boolean running) {
        var cluster = mock(HazelcastCluster.class);
        var cacheManager = mock(CacheManager.class);
        var hazelcast = mock(HazelcastInstance.class);
        var lifecycle = mock(LifecycleService.class);
        var node = mock(HazelcastClusterNode.class);

        when(cluster.getCacheManager()).thenReturn(cacheManager);
        when(cacheManager.vendor()).thenReturn(hazelcast);
        when(hazelcast.getLifecycleService()).thenReturn(lifecycle);
        when(lifecycle.isRunning()).thenReturn(running);
        when(cluster.getLocalNode()).thenReturn(node);
        when(node.getNodeName()).thenReturn("node-1");
        return cluster;
    }

    private PipelineWorkerState state(
            StepRecord record, boolean stopRequested, long startedAt) {
        var state = mock(PipelineWorkerState.class);
        when(state.getCurrentStepRecord()).thenReturn(record);
        when(state.getStopRequested())
                .thenReturn(new AtomicBoolean(stopRequested));
        when(state.getStartedAt()).thenReturn(startedAt);
        return state;
    }

    private Pipeline pipeline(String... stepIds) {
        var steps = java.util.Arrays.stream(stepIds).map(stepId -> {
            var step = mock(Step.class);
            when(step.getId()).thenReturn(stepId);
            return step;
        }).toList();
        return new Pipeline("pipe-1", steps);
    }
}
