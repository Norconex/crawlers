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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineManager;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanCluster;
import com.norconex.crawler.core2.util.ConcurrentUtil;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class InfinispanPipelineManager
        implements PipelineManager, AutoCloseable {

    //TODO make configurable
    private static long TIMEOUT_MS = 60_000;
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
    public CompletableFuture<Void> executePipeline(@NonNull Pipeline pipeline) {
        var isCoordinator = cluster.getLocalNode().isCoordinator();
        var resultFuture = new CompletableFuture<Void>();
        var pipelineId = pipeline.getId();

        CompletableFuture<Void> coordinatorFuture =
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
                                return null;
                            })
                            .thenCompose(
                                    v -> coordinators.get(pipelineId) != null
                                            ? coordinators.get(pipelineId)
                                                    .getCompletionFuture()
                                            : CompletableFuture
                                                    .completedFuture(null));
        }

        LOG.debug("Starting pipeline worker for {} on node {}",
                pipeline.getId(), cluster.getLocalNode().getNodeName());
        var worker = new PipelineWorker(cluster, pipeline);
        workers.put(pipelineId, worker);
        CompletableFuture
                .runAsync(worker::start, executor)
                .exceptionally(ex -> {
                    LOG.error("Pipeline worker failed", ex);
                    resultFuture.completeExceptionally(ex);
                    closeWorker(pipelineId);
                    return null;
                });

        // Determine pipeline completion (coordinator node: coordinator future; other nodes: polling future)
        var pipelineCompletionFuture = isCoordinator
                ? coordinatorFuture
                : CompletableFuture.runAsync(() -> {
                    try {
                        ConcurrentUtil
                                .waitUntil(() -> isPipelineDone(pipeline));
                    } catch (RuntimeException e) {
                        throw e;
                    }
                }, executor);

        pipelineCompletionFuture.whenComplete((v, ex) -> {
            if (ex != null) {
                closeWorker(pipelineId);
                closeCoordinator(pipelineId);
                resultFuture.completeExceptionally(ex);
            } else {
                // Coordinator already closed itself via its start() -> close(); ensure we close worker.
                closeWorker(pipelineId);
                closeCoordinator(pipelineId); // no-op if already closed/removed
                if (!resultFuture.isDone()) {
                    resultFuture.complete(null);
                }
            }
        });

        // Return the pipeline result future immediately.
        return resultFuture;
    }

    private boolean isPipelineDone(Pipeline pipeline) {
        var pipelineCache = cluster
                .getCacheManager()
                .getPipelineCurrentStepCache();
        var key = CacheKeys.pipelineKey(cluster, pipeline);
        return pipelineCache
                .get(key)
                .filter(rec -> {
                    if (rec.getStatus().isTerminal()) {
                        LOG.info("Pipeline terminated with status: {}",
                                rec.getStatus());
                        return true;
                    }
                    if (System.currentTimeMillis()
                            - rec.getUpdatedAt() > TIMEOUT_MS) {
                        LOG.info("Pipeline timed out, terminating.");
                        return true;
                    }
                    return false;
                })
                .isPresent();

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