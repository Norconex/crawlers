package com.norconex.crawler.core.cluster.impl.infinispan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.infinispan.lifecycle.ComponentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.session.CrawlSession;

@Timeout(10)
class PipelineWorkerStateTest {

    @Test
    void pushWorkerStatusUsesBestEffortWhenInfinispanAdapter() {
        var cluster = mock(InfinispanCluster.class);
        var pipeline = mock(Pipeline.class);
        var session = mock(CrawlSession.class);

        @SuppressWarnings("unchecked")
        var adapter = mock(InfinispanCacheAdapter.class);
        @SuppressWarnings("unchecked")
        Cache<StepRecord> workerCache = (Cache<StepRecord>) adapter;
        @SuppressWarnings("unchecked")
        Cache<StepRecord> stepCache = (Cache<StepRecord>) mock(Cache.class);

        var cacheManager = mock(InfinispanCacheManager.class);
        var dcm = mock(org.infinispan.manager.DefaultCacheManager.class);

        when(cluster.getCrawlSession()).thenReturn(session);
        when(session.getCrawlRunId()).thenReturn("run-1");
        when(cluster.getCacheManager()).thenReturn(cacheManager);
        when(cacheManager.getPipelineStepCache()).thenReturn(stepCache);
        when(cacheManager.getPipelineWorkerStatusCache())
                .thenReturn(workerCache);
        when(cacheManager.vendor()).thenReturn(dcm);
        when(dcm.getStatus()).thenReturn(ComponentStatus.RUNNING);
        when(cluster.getLocalNode())
                .thenReturn(mock(InfinispanClusterNode.class));
        when(cluster.getNodeNames()).thenReturn(List.of("node-1"));
        when(pipeline.getId()).thenReturn("pipe-1");

        var onNewStep = new AtomicReference<StepRecord>();
        Consumer<StepRecord> consumer = onNewStep::set;

        var state = new PipelineWorkerState(cluster, pipeline, consumer);

        state.pushWorkerStatus(PipelineStatus.RUNNING);

        // The constructor also starts a heartbeat that may invoke
        // pushWorkerStatus; we only assert that best-effort is used,
        // not the exact number of invocations.
        verify(adapter, atLeastOnce())
                .putBestEffort(any(String.class), any(StepRecord.class));
    }

    @Test
    void pushWorkerStatusSwallowsExceptionsFromAdapter() {
        var cluster = mock(InfinispanCluster.class);
        var pipeline = mock(Pipeline.class);
        var session = mock(CrawlSession.class);

        @SuppressWarnings("unchecked")
        var adapter = mock(InfinispanCacheAdapter.class);
        @SuppressWarnings("unchecked")
        Cache<StepRecord> workerCache = (Cache<StepRecord>) adapter;
        @SuppressWarnings("unchecked")
        Cache<StepRecord> stepCache = (Cache<StepRecord>) mock(Cache.class);

        var cacheManager = mock(InfinispanCacheManager.class);
        var dcm = mock(org.infinispan.manager.DefaultCacheManager.class);

        when(cluster.getCrawlSession()).thenReturn(session);
        when(session.getCrawlRunId()).thenReturn("run-1");
        when(cluster.getCacheManager()).thenReturn(cacheManager);
        when(cacheManager.getPipelineStepCache()).thenReturn(stepCache);
        when(cacheManager.getPipelineWorkerStatusCache())
                .thenReturn(workerCache);
        when(cacheManager.vendor()).thenReturn(dcm);
        when(dcm.getStatus()).thenReturn(ComponentStatus.RUNNING);
        when(cluster.getLocalNode())
                .thenReturn(mock(InfinispanClusterNode.class));
        when(cluster.getNodeNames()).thenReturn(List.of("node-1"));
        when(pipeline.getId()).thenReturn("pipe-1");

        doThrow(new RuntimeException("boom"))
                .when(adapter)
                .putBestEffort(any(String.class), any(StepRecord.class));

        var onNewStep = new AtomicReference<StepRecord>();
        Consumer<StepRecord> consumer = onNewStep::set;

        var state = new PipelineWorkerState(cluster, pipeline, consumer);

        // This should not throw, even though the adapter does
        state.pushWorkerStatus(PipelineStatus.RUNNING);

        // We only care that the adapter was invoked and the exception
        // did not escape. Because PipelineWorkerState also has a
        // heartbeat scheduler that calls pushWorkerStatus, we allow
        // one or more invocations here.
        verify(adapter, atLeastOnce())
                .putBestEffort(any(String.class), any(StepRecord.class));

        // State object is still usable: it can accept another status
        state.pushWorkerStatus(PipelineStatus.COMPLETED);
        verify(adapter, atLeastOnce())
                .putBestEffort(any(String.class), any(StepRecord.class));
    }
}
