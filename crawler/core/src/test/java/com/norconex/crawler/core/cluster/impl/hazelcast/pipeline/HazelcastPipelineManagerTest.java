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
package com.norconex.crawler.core.cluster.impl.hazelcast.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastCluster;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnectorConfig;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastTestSupport;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.session.CrawlSession;

/**
 * Unit/component tests for the guard paths and query methods of
 * {@link HazelcastPipelineManager} that do not require a live in-flight
 * pipeline execution.
 */
@Timeout(60)
@WithTestWatcherLogging
class HazelcastPipelineManagerTest {

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

    // -----------------------------------------------------------------------
    // getPipelineProgress — no active execution
    // -----------------------------------------------------------------------

    @Test
    void getPipelineProgress_noActivePipeline_returnsPendingStatus()
            throws IOException {
        var mgr = startManager("no-exec");

        var progress = mgr.getPipelineProgress("nonexistent-pipeline");

        assertThat(progress).isNotNull();
        assertThat(progress.getStatus()).isSameAs(PipelineStatus.PENDING);
    }

    // -----------------------------------------------------------------------
    // stopPipeline — unknown id
    // -----------------------------------------------------------------------

    @Test
    void stopPipeline_unknownId_returnsAlreadyCompletedFuture()
            throws IOException {
        var mgr = startManager("stop-unknown");

        var future = mgr.stopPipeline("no-such-pipeline");

        assertThat(future).isNotNull();
        assertThat(future.isDone()).isTrue();
        assertThatCode(() -> future.get()).doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // stop() — no active pipelines
    // -----------------------------------------------------------------------

    @Test
    void stop_withNoPipelinesRegistered_doesNotThrow() throws IOException {
        var mgr = startManager("stop-empty");

        assertThatCode(mgr::stop).doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // close() / closed guard
    // -----------------------------------------------------------------------

    @Test
    void close_thenExecutePipeline_throwsIllegalState() throws IOException {
        var mgr = startManager("closed-guard");
        var pipeline = new Pipeline("pipe-id");

        mgr.close();

        assertThatThrownBy(() -> mgr.executePipeline(pipeline))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    // -----------------------------------------------------------------------
    // Step-change listeners
    // -----------------------------------------------------------------------

    @Test
    void addAndRemoveStepChangeListener_doesNotThrow() throws IOException {
        var mgr = startManager("step-listener");

        com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListener<
                StepRecord> listener =
                        (k, v) -> {};

        assertThatCode(() -> mgr.addStepChangeListener(listener))
                .doesNotThrowAnyException();
        assertThatCode(() -> mgr.removeStepChangeListener(listener))
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // Worker-status listeners
    // -----------------------------------------------------------------------

    @Test
    void addAndRemoveWorkerStatusListener_doesNotThrow() throws IOException {
        var mgr = startManager("worker-listener");

        com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListener<
                StepRecord> listener =
                        (k, v) -> {};

        assertThatCode(() -> mgr.addWorkerStatusListener(listener))
                .doesNotThrowAnyException();
        assertThatCode(() -> mgr.removeWorkerStatusListener(listener))
                .doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private HazelcastPipelineManager startManager(String suffix)
            throws IOException {
        var workDir = Files.createDirectories(
                tempDir.resolve("hz-pm-" + suffix));
        var config = new HazelcastClusterConnectorConfig()
                .setClusterName("pm-test-" + UUID.randomUUID()
                        .toString().substring(0, 8));
        cluster = HazelcastTestSupport.newCluster(config);
        cluster.init(workDir, false);

        // Bind a minimal mock session so actions that reference it don't NPE
        var mockSession = org.mockito.Mockito.mock(CrawlSession.class);
        org.mockito.Mockito.when(mockSession.getCrawlerId())
                .thenReturn("test-crawler");
        org.mockito.Mockito.when(mockSession.getCrawlSessionId())
                .thenReturn("sess-" + suffix);
        org.mockito.Mockito.when(mockSession.getCrawlRunId())
                .thenReturn("run-" + suffix);
        cluster.bindSession(mockSession);

        return (HazelcastPipelineManager) cluster.getPipelineManager();
    }
}
