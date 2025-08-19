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
package com.norconex.crawler.core.cluster.impl.infinispan;

import static java.util.Optional.ofNullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineManager;
import com.norconex.crawler.core.cluster.pipeline.PipelineResult;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanCluster;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class InfinispanPipelineManager
        implements PipelineManager, AutoCloseable {

    private static final long SHUTDOWN_AWAIT_SECONDS = 5;

    private final InfinispanCluster cluster;

    private final ExecutorService executor =
            Executors.newCachedThreadPool(new ThreadFactory() {
                private final ThreadFactory delegate =
                        Executors.defaultThreadFactory();
                private final AtomicInteger seq = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    var t = delegate.newThread(r);
                    t.setName("PIPE-" + cluster.getLocalNode().getNodeName()
                            + "-" + seq.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            });

    private final Map<String, PipelineWorker> workers =
            new ConcurrentHashMap<>();
    private final Map<String, PipelineCoordinator> coordinators =
            new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<PipelineResult>
            executePipeline(@NonNull Pipeline pipeline, long timeoutMs) {
        var isCoordinator = cluster.getLocalNode().isCoordinator();
        var resultFuture = new CompletableFuture<PipelineResult>();
        var pipelineId = pipeline.getId();

        CompletableFuture<PipelineResult> coordinatorFuture =
                CompletableFuture.completedFuture(null);

        if (isCoordinator) {
            LOG.debug("Starting pipeline coordinator for {} on node {}",
                    pipeline.getId(), cluster.getLocalNode().getNodeName());
            var coordinator = new PipelineCoordinator(cluster, pipeline);
            coordinators.put(pipelineId, coordinator);
            coordinatorFuture =
                    CompletableFuture.runAsync(coordinator::start, executor)
                            .exceptionally(ex -> {
                                LOG.error("Pipeline coordinator failed", ex);
                                resultFuture.completeExceptionally(ex);
                                closeCoordinator(pipelineId);
                                return null; // swallow here; resultFuture already exceptional
                            })
                            .thenCompose(v -> {
                                var c = coordinators.get(pipelineId);
                                return c != null ? c.getCompletionFuture()
                                        : CompletableFuture.<
                                                PipelineResult>completedFuture(
                                                        null);
                            });
        }

        LOG.debug("Starting pipeline worker for {} on node {}",
                pipeline.getId(), cluster.getLocalNode().getNodeName());
        var worker = new PipelineWorker(cluster, pipeline);
        workers.put(pipelineId, worker);
        CompletableFuture.runAsync(worker::start, executor)
                .exceptionally(ex -> {
                    LOG.error("Pipeline worker failed", ex);
                    resultFuture.completeExceptionally(ex);
                    closeWorker(pipelineId);
                    return null;
                });

        var pipelineCompletionFuture = isCoordinator
                ? coordinatorFuture
                : CompletableFuture.supplyAsync(
                        () -> buildWorkerSideResult(pipeline, timeoutMs),
                        executor);

        pipelineCompletionFuture.whenComplete((res, ex) -> {
            if (ex != null) {
                closeWorker(pipelineId);
                closeCoordinator(pipelineId);
                resultFuture.completeExceptionally(ex);
            } else {
                closeWorker(pipelineId);
                closeCoordinator(pipelineId);
                if (!resultFuture.isDone()) {
                    resultFuture.complete(res);
                }
            }
        });
        return resultFuture;
    }

    @Override
    public CompletableFuture<Void> stopPipeline(
            String pipelineId, long timeout) {
        //Stop corresponding worker and then possible coordinator
        //TODO implement properly
        ofNullable(workers.get(pipelineId)).ifPresent(PipelineWorker::stop);
        ofNullable(coordinators.get(pipelineId))
                .ifPresent(PipelineCoordinator::stop);
        return null;
    }

    private PipelineResult buildWorkerSideResult(
            Pipeline pipeline, long timeout) {
        var pipelineCache =
                cluster.getCacheManager().getPipelineCurrentStepCache();
        var key = CacheKeys.pipelineKey(cluster, pipeline);
        var done = false;
        var timedOut = false;
        StepRecord rec = null;
        while (!done) {
            rec = pipelineCache.get(key).orElse(null);
            if (rec != null && rec.getStatus() != null
                    && rec.getStatus().isTerminal()) {
                done = true;
            } else if (timeout > 0 && rec != null && (System.currentTimeMillis()
                    - rec.getUpdatedAt() > timeout)) {
                done = true;
                timedOut = true;
            } else {
                Sleeper.sleepMillis(250);
            }
        }
        var finishedAt = System.currentTimeMillis();
        PipelineStatus status;
        String lastStepId;
        status = rec.getStatus();
        lastStepId = rec.getStepId();
        if (timedOut && !status.isTerminal()) {
            status = PipelineStatus.EXPIRED;
        }
        return PipelineResult.builder()
                .pipelineId(pipeline.getId())
                .status(status)
                .lastStepId(lastStepId)
                .startedAt(0L)
                .finishedAt(finishedAt)
                .resumed(false)
                .timedOut(timedOut)
                .build();
    }

    private void closeWorker(String pipelineId) {
        var w = workers.remove(pipelineId);
        if (w != null) {
            try {
                w.close();
            } catch (Exception e) {
                LOG.debug("Error closing worker for pipeline {}: {}",
                        pipelineId, e.toString());
            }
        }
    }

    private void closeCoordinator(String pipelineId) {
        var c = coordinators.remove(pipelineId);
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
                LOG.debug("Error closing coordinator for pipeline {}: {}",
                        pipelineId, e.toString());
            }
        }
    }

    @Override
    public void close() {
        executor.shutdown(); // disable new tasks, let running complete
        try {
            if (!executor.awaitTermination(SHUTDOWN_AWAIT_SECONDS,
                    TimeUnit.SECONDS)) {
                executor.shutdownNow(); // cancel running tasks
                if (!executor.awaitTermination(SHUTDOWN_AWAIT_SECONDS,
                        TimeUnit.SECONDS)) {
                    LOG.warn("Executor did not terminate cleanly.");
                }
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        workers.values().forEach(w -> {
            try {
                w.close();
            } catch (Exception ignore) {
            }
        });
        workers.clear();
        coordinators.values().forEach(c -> {
            try {
                c.close();
            } catch (Exception ignore) {
            }
        });
        coordinators.clear();
    }
}