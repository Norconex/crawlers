/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.cluster.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListener;

@Timeout(30)
class PipelineProgressBeanTest {

    static class PipeManagerStub implements PipelineManager {
        private final PipelineProgress progress;

        PipeManagerStub(PipelineProgress progress) {
            this.progress = progress;
        }

        @Override
        public CompletableFuture<PipelineResult>
                executePipeline(Pipeline pipeline) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void>
                stopPipeline(String pipelineId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PipelineProgress getPipelineProgress(String pipelineId) {
            return progress;
        }

        @Override
        public void stop() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addStepChangeListener(
                CacheEntryChangeListener<StepRecord> listener) {
            // no-op
        }

        @Override
        public void removeStepChangeListener(
                CacheEntryChangeListener<StepRecord> listener) {
            // no-op
        }

        @Override
        public void addWorkerStatusListener(
                CacheEntryChangeListener<StepRecord> listener) {
            // no-op
        }

        @Override
        public void removeWorkerStatusListener(
                CacheEntryChangeListener<StepRecord> listener) {
            // no-op
        }
    }

    @Test
    void testMapsProgressFields() {
        var p = PipelineProgress.builder()
                .status(PipelineStatus.RUNNING)
                .currentStepId("crawlDocuments")
                .currentStepIndex(1)
                .stepCount(3)
                .stepProgress(0.42f)
                .stepMessage("processed=420, queued=580")
                .build();
        var bean = new PipelineProgressBean(new PipeManagerStub(p), "any");

        assertThat(bean.getStatus()).isEqualTo("RUNNING");
        assertThat(bean.getCurrentStepId()).isEqualTo("crawlDocuments");
        assertThat(bean.getCurrentStepIndex()).isEqualTo(1);
        assertThat(bean.getStepCount()).isEqualTo(3);
        assertThat(bean.getStepProgress()).isEqualTo(0.42f);
        assertThat(bean.getStepMessage())
                .isEqualTo("processed=420, queued=580");
    }

    @Test
    void testNullStatusHandledGracefully() {
        var p = PipelineProgress.builder().build(); // all nulls/defaults
        var bean = new PipelineProgressBean(new PipeManagerStub(p), "pipe-1");

        assertThat(bean.getStatus()).isNull();
        assertThat(bean.getCurrentStepId()).isNull();
        assertThat(bean.getCurrentStepIndex()).isZero();
        assertThat(bean.getStepCount()).isZero();
    }

    @Test
    void testExceptionInManagerReturnsEmptyProgress() {
        // When getPipelineProgress throws, snapshot() catches and returns
        // an empty PipelineProgress.
        var bean = new PipelineProgressBean(new PipelineManager() {
            @Override
            public CompletableFuture<PipelineResult> executePipeline(
                    Pipeline pipeline) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<Void> stopPipeline(String pipelineId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public PipelineProgress getPipelineProgress(String pipelineId) {
                throw new RuntimeException("simulated failure");
            }

            @Override
            public void stop() {
            }

            @Override
            public void addStepChangeListener(
                    CacheEntryChangeListener<StepRecord> listener) {
            }

            @Override
            public void removeStepChangeListener(
                    CacheEntryChangeListener<StepRecord> listener) {
            }

            @Override
            public void addWorkerStatusListener(
                    CacheEntryChangeListener<StepRecord> listener) {
            }

            @Override
            public void removeWorkerStatusListener(
                    CacheEntryChangeListener<StepRecord> listener) {
            }
        }, "pipe-x");

        assertThat(bean.getStatus()).isNull();
        assertThat(bean.getStepCount()).isZero();
    }
}
