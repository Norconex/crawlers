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

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.cluster.impl.infinispan.event.CacheEntryChangeListener;
import com.norconex.crawler.core.cluster.pipeline.Pipeline;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.Step;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.util.ConcurrentUtil;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PipelineWorkerState implements AutoCloseable {
    private final InfinispanCluster cluster;
    private final Pipeline pipeline;
    private final CrawlSession session;

    // Cache Keys
    private final String pipelineKey; // sessionId:pipelineId
    private final String workerKey; // sessionId:pipelineId:runId:nodeName

    // Caches
    private final Cache<StepRecord> pipelineStepCache;
    private final Cache<StepRecord> workerStatusCache;

    // Flags
    @Getter
    private final AtomicBoolean closed = new AtomicBoolean(false);
    @Getter
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    // Current Step
    @Getter
    private final StepRecord currentStepRecord;
    // Worker Status
    private PipelineStatus workerStatus = PipelineStatus.PENDING;

    private final ScheduledExecutorService statusUpdater =
            Executors.newSingleThreadScheduledExecutor(
                    new BasicThreadFactory.Builder()
                            .namingPattern("WORKER-STATUS-UPDATER")
                            .build());
    private ScheduledFuture<?> workerStatusFuture;

    // Misc.
    private final Consumer<StepRecord> onNewStepRun;
    @Getter
    private final long startedAt;
    // Use concurrent set to avoid races when listener fires concurrently
    @Getter
    private final Set<String> encounteredSteps = ConcurrentHashMap.newKeySet();
    // listener reference so we can remove it on close
    private CacheEntryChangeListener<StepRecord> pipelineStepListener;
    private final CompletableFuture<Void> completion =
            new CompletableFuture<>();

    public PipelineWorkerState(
            @NonNull InfinispanCluster cluster,
            @NonNull Pipeline pipeline,
            @NonNull Consumer<StepRecord> onNewStepRun) {
        this.cluster = cluster;
        this.pipeline = pipeline;
        this.onNewStepRun = onNewStepRun;
        session = cluster.getCrawlSession();
        startedAt = System.currentTimeMillis();

        pipelineKey = CacheKeys.pipelineKey(cluster, pipeline);
        workerKey = CacheKeys.pipelineWorkerNodeKey(cluster, pipeline);

        pipelineStepCache = cluster.getCacheManager().getPipelineStepCache();
        workerStatusCache =
                cluster.getCacheManager().getPipelineWorkerStatusCache();

        // no step, no updatedAt yet
        currentStepRecord = new StepRecord()
                .setPipelineId(pipeline.getId())
                .setRunId(session.getCrawlRunId())
                .setStatus(PipelineStatus.PENDING);

        initStepListener();

        //TODO make status update heartbeat configurable
        // 1 second may be taxing
        final var iterCount = new AtomicInteger();
        workerStatusFuture = statusUpdater.scheduleAtFixedRate(() -> {
            if (!closed.get()) {
                pushWorkerStatus(workerStatus);

                // every 15 iteration (15 sec.s) update pipeline status
                // in case we missed the latest
                if (iterCount.getAndIncrement() % 15 == 0) {
                    iterCount.set(1);
                    // in case we are joining after a RUNNING event, grab from cache
                    // for the first time
                    updateCurrentStepRecord(currentRec -> {
                        var rec = pipelineStepCache
                                .get(pipelineKey).orElse(null);
                        logStepRecordReceived(rec, "explicit check");
                        if (rec == null) {
                            currentRec.setStatus(PipelineStatus.PENDING)
                                    .setStepId(null)
                                    .setUpdatedAt(0);
                        } else {
                            currentRec.setStatus(rec.getStatus())
                                    .setStepId(rec.getStepId())
                                    .setUpdatedAt(rec.getUpdatedAt());
                        }
                    });
                }
            }
            //TODO make heartbeat configurable, keep 1 for testing
        }, 0, 1, TimeUnit.SECONDS); // short heartbeat to avoid false expiry
    }

    public Step getCurrentStep() {
        return pipeline.getStep(currentStepRecord.getStepId());
    }

    public void pushWorkerStatus(PipelineStatus status) {
        workerStatus = status;
        if (!InfinispanUtil.isClusterRunning(cluster)) {
            LOG.debug("Cluster reported as not running by node {}",
                    cluster.getLocalNode().getNodeName());
            return;
        }

        var rec = new StepRecord()
                .setPipelineId(pipeline.getId())
                .setStepId(currentStepRecord.getStepId())
                .setStatus(status)
                .setUpdatedAt(System.currentTimeMillis())
                .setRunId(session.getCrawlRunId());
        try {
            workerStatusCache.put(workerKey, rec);
        } catch (org.infinispan.commons.TimeoutException te) {
            LOG.warn("Skipping worker status update for {} due to cache "
                    + "lock timeout on key {}.",
                    pipeline.getId(), workerKey);
        } catch (RuntimeException e) {
            LOG.debug("Worker status update failed for {}: {}",
                    pipeline.getId(), e.toString());
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            LOG.debug("Closing PipelineWorker for pipeline {}",
                    pipeline.getId());
            if (workerStatusFuture != null) {
                workerStatusFuture.cancel(true);
                workerStatusFuture = null;
            }
            // attempt graceful shutdown of scheduler
            ConcurrentUtil.shutdownAndAwait(statusUpdater,
                    Duration.ofSeconds(2));

            if (pipelineStepListener != null) {
                try {
                    cluster.getPipelineManager()
                            .removeStepChangeListener(pipelineStepListener);
                } catch (Exception e) {
                    LOG.debug("Could not remove step listener for "
                            + "pipeline {}: {}",
                            pipeline.getId(), e.toString());
                }
                pipelineStepListener = null;
            }
            LOG.debug("PipelineWorker closed for pipeline {}",
                    pipeline.getId());
            completion.complete(null);
        }
    }

    //--- Private methods ------------------------------------------------------

    private void logStepRecordReceived(StepRecord rec, String source) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("""
                Worker node {} {} for \
                pipeline {} active step got: "{}" -> {}.""",
                    cluster.getLocalNode().getNodeName(),
                    source,
                    pipeline.getId(),
                    rec == null ? null : rec.getStepId(),
                    rec == null ? null : rec.getStatus());
        }
    }

    private void initStepListener() {

        pipelineStepListener = (key, pipeRec) -> {
            logStepRecordReceived(pipeRec, "listener");
            // Only accept updates for this exact run’s key
            if (!pipelineKey.equals(key)) {
                return;
            }

            if (isStepForThisRun(pipeRec)) {
                updateCurrentStepRecord(curRec -> {
                    curRec.setStatus(pipeRec.getStatus())
                            .setStepId(pipeRec.getStepId())
                            .setUpdatedAt(pipeRec.getUpdatedAt());
                });
            }
        };
        cluster.getPipelineManager()
                .addStepChangeListener(pipelineStepListener);
    }

    private void updateCurrentStepRecord(Consumer<StepRecord> stepUpdater) {
        var oldStatus = currentStepRecord.getStatus();
        var oldStepId = currentStepRecord.getStepId();
        stepUpdater.accept(currentStepRecord);
        var newStatus = currentStepRecord.getStatus();
        var newStepId = currentStepRecord.getStepId();

        //TODO is expiry something to check here?

        // if newly set to run, run it.
        // if already running, run it if step id has changed
        if (newStatus.isRunning()
                && (!oldStatus.isRunning() || !oldStepId.equals(newStepId))) {
            LOG.info("{}:{} -> {}:{}",
                    oldStepId, oldStatus, newStepId, newStatus);
            onNewStepRun.accept(currentStepRecord);
        } else {
            LOG.debug("{}:{} -> {}:{}",
                    oldStepId, oldStatus, newStepId, newStatus);
        }
    }

    private boolean isStepForThisRun(StepRecord pipeStepRec) {
        // Ignore records for other runs within the same session
        if (!session.getCrawlRunId().equals(pipeStepRec.getRunId())) {
            LOG.debug("Ignoring step update for other runId {} (current {}).",
                    pipeStepRec.getRunId(), session.getCrawlRunId());
            return false;
        }
        return true;
    }
}