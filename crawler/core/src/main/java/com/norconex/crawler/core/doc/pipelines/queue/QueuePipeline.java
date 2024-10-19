/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.queue;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.norconex.commons.lang.function.Predicates;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.tasks.CrawlerTaskContext;
import com.norconex.crawler.core.util.LogUtil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QueuePipeline implements Consumer<QueuePipelineContext> {

    private final QueueInitializer initializer;
    private final Predicates<QueuePipelineContext> stages;
    @Getter
    private final Function<QueuePipelineContext,
            ? extends QueuePipelineContext> contextAdapter;

    private MutableBoolean queueInitialized;

    @Builder
    private QueuePipeline(
            @NonNull Predicates<QueuePipelineContext> stages,
            Function<QueuePipelineContext,
                    ? extends QueuePipelineContext> contextAdapter,
            QueueInitializer initializer) {
        this.stages = stages;
        this.contextAdapter = contextAdapter;
        this.initializer = initializer;
    }

    public boolean isQueueInitialized() {
        return queueInitialized != null && queueInitialized.booleanValue();
    }

    /**
     * Queues all initial references. If configured to be asynchronous, the
     * method returns right away and you should either watch the
     * {@link MutableBoolean} returned, or call {@link #isQueueInitialized()}
     * to find out if it is done.
     * Otherwise the method returns when initial queuing has completed.
     * If no queue initializer was provided, returns right away with
     * <code>true</code> (initialized).
     * @param crawler the crawler
     * @return queue initialization completion status
     */
    public MutableBoolean initializeQueue(CrawlerTaskContext crawler) {
        //--- Queue initial references ---------------------------------
        //TODO if we resume, shall we not queue again? What if it stopped
        // in the middle of initial queuing, then to be safe we have to
        // queue again and expect that those that were already processed
        // will simply be ignored (default behavior).
        // Consider persisting a flag that would tell us if we are resuming
        // with an incomplete queue initialization, or make initialization
        // more sophisticated so we can resume in the middle of it
        // (this last option would likely be very impractical).
        if (initializer != null) {
            LOG.info("Queueing initial references...");
            var queueInitContext = new QueueInitContext(
                    crawler,
                    crawler.getState().isResuming(),
                    rec -> accept(new QueuePipelineContext(crawler, rec)));
            var cfg = crawler.getConfiguration();
            if (cfg.isStartReferencesAsync()) {
                queueInitialized = initializeQueueAsync(queueInitContext);
            } else {
                queueInitialized = initializeQueueSync(queueInitContext);
            }
        } else {
            queueInitialized = new MutableBoolean(true);
        }
        return queueInitialized;
    }

    @Override
    public void accept(QueuePipelineContext context) {
        var ctx = contextAdapter != null
                ? contextAdapter.apply(context)
                : context;
        stages.test(ctx);
    }

    private MutableBoolean initializeQueueAsync(QueueInitContext ctx) {
        var doneStatus = new MutableBoolean(false);
        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                LogUtil.setMdcCrawlerId(ctx.getCrawler().getId());
                Thread.currentThread().setName(ctx.getCrawler().getId());
                LOG.info("Queuing start references asynchronously.");
                initializer.accept(ctx);
                doneStatus.setTrue();
            });
            return doneStatus;
        } catch (Exception e) {
            doneStatus.setTrue();
            return doneStatus;
        } finally {
            try {
                executor.shutdown();
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.error("Reading of start references interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private MutableBoolean initializeQueueSync(QueueInitContext ctx) {
        LOG.info("Queuing start references synchronously.");
        initializer.accept(ctx);
        return new MutableBoolean(true);
    }

    /**
     * Holds contextual objects necessary to initialize a crawler queue.
     */
    @Accessors(fluent = false)
    @AllArgsConstructor
    public static class QueueInitContext {
        @Getter
        private final CrawlerTaskContext crawler;
        @Getter
        private final boolean resuming;
        private final Consumer<CrawlDocContext> queuer;

        public void queue(@NonNull String reference) {
            var rec = crawler.newDocContext(reference);
            rec.setDepth(0);
            queue(rec);
        }

        public void queue(@NonNull CrawlDocContext rec) {
            queuer.accept(rec);
        }
    }
}
