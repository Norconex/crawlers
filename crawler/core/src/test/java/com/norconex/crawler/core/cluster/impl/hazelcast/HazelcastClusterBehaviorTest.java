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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.cluster.impl.hazelcast.event.CoordinatorChangeListener;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.session.CrawlSession;

import org.apache.commons.collections4.Bag;

/**
 * Tests for uncovered {@link HazelcastCluster} methods: session binding,
 * coordinator-change listeners, monitoring, and
 * {@link HazelcastUtil#currentPipelineStepRecordOrFirst} /
 * {@link HazelcastUtil#waitForClusterWarmUp}.
 */
@Timeout(60)
@WithTestWatcherLogging
class HazelcastClusterBehaviorTest {

    @TempDir
    java.nio.file.Path tempDir;

    private HazelcastCluster cluster;

    @AfterEach
    void tearDown() {
        if (cluster != null) {
            cluster.close();
            cluster = null;
        }
        HazelcastTestSupport.shutdownAll();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private HazelcastCluster startCluster(String suffix) throws IOException {
        var workDir = Files.createDirectories(
                tempDir.resolve("hz-behavior-" + suffix));
        var config = new HazelcastClusterConnectorConfig()
                .setClusterName("behavior-test-" + UUID.randomUUID()
                        .toString().substring(0, 8));
        cluster = HazelcastTestSupport.newCluster(config);
        cluster.init(workDir, false);
        return cluster;
    }

    private static Step mockStep(String id) {
        return new Step() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public boolean isDistributed() {
                return false;
            }

            @Override
            public void execute(CrawlSession s) {
            }

            @Override
            public void stop(CrawlSession s) {
            }

            @Override
            public PipelineStatus reduce(CrawlSession s,
                    Bag<PipelineStatus> b) {
                return PipelineStatus.COMPLETED;
            }
        };
    }

    // ------------------------------------------------------------------
    // getCrawlerId
    // ------------------------------------------------------------------

    @Test
    void getCrawlerId_noSessionBound_returnsNull() throws IOException {
        startCluster("no-session");
        assertThat(cluster.getCrawlerId()).isNull();
    }

    @Test
    void getCrawlerId_afterBindSession_returnsCrawlerId() throws IOException {
        startCluster("with-session");
        var mockSession = mock(CrawlSession.class);
        when(mockSession.getCrawlerId()).thenReturn("my-crawler");
        cluster.bindSession(mockSession);

        assertThat(cluster.getCrawlerId()).isEqualTo("my-crawler");
    }

    // ------------------------------------------------------------------
    // addCoordinatorChangeListener / removeCoordinatorChangeListener
    // ------------------------------------------------------------------

    @Test
    void addCoordinatorChangeListener_firesImmediatelyWithCurrentState()
            throws IOException {
        startCluster("coord-listener");

        var fired = new AtomicBoolean(false);
        var firedValue = new AtomicBoolean(false);

        CoordinatorChangeListener listener = isCoordinator -> {
            fired.set(true);
            firedValue.set(isCoordinator);
        };

        cluster.addCoordinatorChangeListener(listener);

        // Listener must have been called synchronously on registration
        assertThat(fired.get()).isTrue();
        // In a standalone single-node cluster the local node is always
        // coordinator
        assertThat(firedValue.get()).isTrue();
    }

    @Test
    void removeCoordinatorChangeListener_removedListenerNotInvoked()
            throws IOException {
        startCluster("remove-listener");

        var callCount = new java.util.concurrent.atomic.AtomicInteger(0);
        CoordinatorChangeListener listener =
                ignored -> callCount.incrementAndGet();

        cluster.addCoordinatorChangeListener(listener);
        int afterAdd = callCount.get(); // fired once on registration

        cluster.removeCoordinatorChangeListener(listener);
        // Add a dummy to trigger an immediate-fire callback to confirm the
        // removed listener is no longer registered
        cluster.addCoordinatorChangeListener(ignored -> {});

        // callCount must not have increased after removal
        assertThat(callCount.get()).isEqualTo(afterAdd);
    }

    // ------------------------------------------------------------------
    // startStopMonitoring
    // ------------------------------------------------------------------

    @Test
    void startStopMonitoring_doesNotThrow() throws IOException {
        startCluster("stop-mon");
        assertThatCode(() -> cluster.startStopMonitoring())
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // isStandalone / isClustered
    // ------------------------------------------------------------------

    @Test
    void isStandalone_standaloneCluster_returnsTrue() throws IOException {
        startCluster("standalone");
        assertThat(cluster.isStandalone()).isTrue();
        assertThat(cluster.isClustered()).isFalse();
    }

    // ------------------------------------------------------------------
    // HazelcastUtil.waitForClusterWarmUp
    // ------------------------------------------------------------------

    @Test
    void waitForClusterWarmUp_singleNode_completesWithoutTimeout()
            throws IOException {
        startCluster("warmup");
        // Should return well within the 60s test timeout for a single node
        assertThatCode(
                () -> HazelcastUtil.waitForClusterWarmUp(cluster))
                        .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------
    // HazelcastUtil.currentPipelineStepRecordOrFirst
    // ------------------------------------------------------------------

    @Test
    void currentPipelineStepRecordOrFirst_noExistingRecord_returnsPendingFirst()
            throws IOException {
        startCluster("pipe-first");

        var mockSession = mock(CrawlSession.class);
        when(mockSession.getCrawlSessionId()).thenReturn("sess-1");
        when(mockSession.getCrawlRunId()).thenReturn("run-1");
        cluster.bindSession(mockSession);

        var pipeline = new Pipeline("my-pipe",
                List.of(mockStep("step-a"), mockStep("step-b")));

        StepRecord rec = HazelcastUtil
                .currentPipelineStepRecordOrFirst(cluster, pipeline);

        assertThat(rec).isNotNull();
        assertThat(rec.getStepId()).isEqualTo("step-a"); // first step
        assertThat(rec.getStatus()).isSameAs(PipelineStatus.PENDING);
        assertThat(rec.getRunId()).isEqualTo("run-1");
        assertThat(rec.getPipelineId()).isEqualTo("my-pipe");
    }

    @Test
    void currentPipelineStepRecordOrFirst_existingRecord_returnsThatRecord()
            throws IOException {
        startCluster("pipe-existing");

        var mockSession = mock(CrawlSession.class);
        when(mockSession.getCrawlSessionId()).thenReturn("sess-2");
        when(mockSession.getCrawlRunId()).thenReturn("run-2");
        cluster.bindSession(mockSession);

        var pipeline = new Pipeline("another-pipe",
                List.of(mockStep("step-x"), mockStep("step-y")));

        // Pre-populate the cache with a RUNNING record
        var pipelineKey = CacheKeys.pipelineKey(cluster, pipeline);
        var existing = new StepRecord()
                .setPipelineId("another-pipe")
                .setStepId("step-y")
                .setStatus(PipelineStatus.RUNNING)
                .setRunId("run-2")
                .setUpdatedAt(System.currentTimeMillis());
        cluster.getCacheManager().getPipelineStepCache()
                .put(pipelineKey, existing);

        StepRecord rec = HazelcastUtil
                .currentPipelineStepRecordOrFirst(cluster, pipeline);

        assertThat(rec.getStepId()).isEqualTo("step-y");
        assertThat(rec.getStatus()).isSameAs(PipelineStatus.RUNNING);
    }

    @Test
    void currentPipelineStepRecordOrFirst_terminalRecord_resetsToPending()
            throws IOException {
        startCluster("pipe-terminal");

        var mockSession = mock(CrawlSession.class);
        when(mockSession.getCrawlSessionId()).thenReturn("sess-3");
        when(mockSession.getCrawlRunId()).thenReturn("run-3");
        cluster.bindSession(mockSession);

        var pipeline = new Pipeline("term-pipe",
                List.of(mockStep("s1")));

        // Pre-populate with a COMPLETED (terminal) record
        var pipelineKey = CacheKeys.pipelineKey(cluster, pipeline);
        var terminal = new StepRecord()
                .setPipelineId("term-pipe")
                .setStepId("s1")
                .setStatus(PipelineStatus.COMPLETED)
                .setRunId("old-run")
                .setUpdatedAt(System.currentTimeMillis());
        cluster.getCacheManager().getPipelineStepCache()
                .put(pipelineKey, terminal);

        StepRecord rec = HazelcastUtil
                .currentPipelineStepRecordOrFirst(cluster, pipeline);

        // Terminal → reset to PENDING with new run id
        assertThat(rec.getStatus()).isSameAs(PipelineStatus.PENDING);
        assertThat(rec.getRunId()).isEqualTo("run-3");
    }
}
