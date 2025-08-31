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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.norconex.crawler.core.cluster.impl.infinispan.event.CoordinatorChangeListener;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineException;
import com.norconex.crawler.core.cluster.pipeline.PipelineResult;
import com.norconex.crawler.core2.util.ExceptionSwallower;

import lombok.extern.slf4j.Slf4j;

/**
 * Execute a worker and, if applicable, a coordinator to execute a pipeline.
 * This class is scoped to a single pipeline execution and not meant to be
 * reused.
 */
@Slf4j
public class PipelineExecution implements AutoCloseable {

    private static final long SHUTDOWN_AWAIT_SECONDS = 5;

    private final InfinispanCluster cluster;
    private final Pipeline pipeline;

    private PipelineCoordinator coordinator;
    private PipelineWorker worker;

    private boolean executed;

    private final CompletableFuture<PipelineResult> resultFuture =
            new CompletableFuture<>();

    private final ExecutorService executor =
            Executors.newCachedThreadPool(createThreadFactory());
    private final CoordinatorChangeListener coordChangeListener;

    private boolean isCoordinator;

    public PipelineExecution(
            InfinispanCluster cluster, Pipeline pipeline) {
        this.cluster = cluster;
        this.pipeline = pipeline;
        coordChangeListener = this::handleCoordinatorChange;
        cluster.addCoordinatorChangeListener(coordChangeListener);
    }

    public CompletableFuture<PipelineResult> execute() {
        if (executed) {
            throw new IllegalStateException(
                    "Pipeline %s already executed or is executing.");
        }
        executed = true;
        logMode("initial role");

        isCoordinator = cluster.getLocalNode().isCoordinator();
        if (isCoordinator) {
            startCoordinator();
        }

        // No matter which node is coordinator at any moment in time,
        // the worker will always complete after the coordinator
        // sends a cluster-wide terminal signal for the last step. So it is
        // safe to only track the worker's future.
        return startWorker();
    }

    private CompletableFuture<PipelineResult> startWorker() {
        LOG.info("Starting pipeline worker for {} on node {}",
                pipeline.getId(), cluster.getLocalNode().getNodeName());
        worker = new PipelineWorker(cluster, pipeline);
        return CompletableFuture.supplyAsync(worker::start, executor)
                .exceptionally(ex -> {
                    ExceptionSwallower.close(worker);
                    // Should not happen(?) given the worker should mark
                    // itself "failed" if exception.
                    throw new PipelineException(
                            "Pipeline %s worker threw an exception it was "
                                    + "not able to handle for node %s.",
                            ex);
                });
    }

    //TODO make distinction between stopping a process on a node vs stopping
    // on the cluster, vs shutting down.
    public CompletableFuture<Void> stopPipeline() {
        //Stop corresponding worker and then possible coordinator
        //TODO implement properly
        ofNullable(worker).ifPresent(PipelineWorker::stop);
        ofNullable(coordinator).ifPresent(PipelineCoordinator::stop);
        close();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        cluster.removeCoordinatorChangeListener(coordChangeListener);
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
        ExceptionSwallower.close(worker, coordinator);
    }

    //--- Private methods ------------------------------------------------------

    private void logMode(String reason) {
        if (LOG.isInfoEnabled()) {
            LOG.info("Pipeline {} node {} role: {} ({}).",
                    pipeline.getId(),
                    cluster.getLocalNode().getNodeName(),
                    isCoordinator ? "COORDINATOR" : "WORKER",
                    reason);
        }
    }

    private void handleCoordinatorChange(boolean isCoord) {
        synchronized (this) {
            isCoordinator = isCoord;
            if (!executed || resultFuture.isDone()) {
                return; // ignore before start or after completion
            }
            // new coordinator:
            if (isCoord && coordinator == null) {
                // Promotion: only if we do not already have a coordinator instance
                logMode("promotion to coordinator");
                startCoordinator();
                //                attachCoordinatorCompletion();
            } else if (!isCoord && coordinator != null) {
                // demotion:
                logMode("demotion from coordinator");
                coordinator.close();
                coordinator = null;
            }
        }
    }

    private void startCoordinator() {
        LOG.debug("Starting pipeline coordinator for {} on node {}",
                pipeline.getId(), cluster.getLocalNode().getNodeName());
        coordinator = new PipelineCoordinator(cluster, pipeline);
        //        coordinatorFuture =
        CompletableFuture.runAsync(coordinator::start, executor)
                .exceptionally(ex -> {
                    LOG.error("Pipeline coordinator failed", ex);
                    resultFuture.completeExceptionally(ex);
                    // swallow here; resultFuture already exceptional
                    ExceptionSwallower.close(coordinator);
                    return null;
                });
    }

    private ThreadFactory createThreadFactory() {
        return new ThreadFactory() {
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
        };
    }
}
