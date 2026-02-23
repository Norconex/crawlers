package com.norconex.crawler.core.cluster.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.cluster.impl.hazelcast.event.CacheEntryChangeListener;

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
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeStepChangeListener(
                CacheEntryChangeListener<StepRecord> listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addWorkerStatusListener(
                CacheEntryChangeListener<StepRecord> listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeWorkerStatusListener(
                CacheEntryChangeListener<StepRecord> listener) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void mapsProgressFields() {
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
}
