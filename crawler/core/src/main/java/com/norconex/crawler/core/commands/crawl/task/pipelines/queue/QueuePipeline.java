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
package com.norconex.crawler.core.commands.crawl.task.pipelines.queue;

import java.util.function.Consumer;
import java.util.function.Function;

import com.norconex.commons.lang.function.Predicates;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QueuePipeline implements Consumer<QueuePipelineContext> {

    //    private final QueueInitializer initializer;
    private final Predicates<QueuePipelineContext> stages;
    @Getter
    private final Function<QueuePipelineContext,
            ? extends QueuePipelineContext> contextAdapter;

    //    private MutableBoolean queueInitialized;

    @Builder
    private QueuePipeline(
            @NonNull Predicates<QueuePipelineContext> stages,
            Function<QueuePipelineContext,
                    ? extends QueuePipelineContext> contextAdapter) {

        //    }, QueueInitializer initializer) {
        this.stages = stages;
        this.contextAdapter = contextAdapter;
        //        this.initializer = initializer;
    }

    //    public boolean isQueueInitialized() {
    //        return queueInitialized != null && queueInitialized.booleanValue();
    //    }

    //    /**
    //     * Queues all initial references. If configured to be asynchronous, the
    //     * method returns right away.
    //     * Otherwise the method returns when all initial references are queued.
    //     * If no queue initializer was provided, returns right away with a
    //     * completed future.
    //     * @param crawlerContext the crawler context
    //     * @return queue initialization completion status
    //     * @see #isQueueInitialized()
    //     */
    //    public Future<?> initializeQueue(CrawlerContext crawlerContext) {
    //        //--- Queue initial references ---------------------------------
    //        //TODO if we resume, shall we not queue again? What if it stopped
    //        // in the middle of initial queuing, then to be safe we have to
    //        // queue again and expect that those that were already processed
    //        // will simply be ignored (default behavior).
    //        // Consider persisting a flag that would tell us if we are resuming
    //        // with an incomplete queue initialization, or make initialization
    //        // more sophisticated so we can resume in the middle of it
    //        // (this last option would likely be very impractical).
    //        if (initializer != null) {
    //            LOG.info("Queueing initial references...");
    //            var queueInitContext = new QueueInitContext(
    //                    crawlerContext,
    //                    crawlerContext.getState().isResuming(),
    //                    rec -> accept(
    //                            new QueuePipelineContext(crawlerContext, rec)));
    //            var cfg = crawlerContext.getConfiguration();
    //            if (cfg.isStartReferencesAsync()) {
    //                return initializeQueueAsync(queueInitContext);
    //            }
    //            return initializeQueueSync(queueInitContext);
    //        }
    //        return CompletableFuture.completedFuture(null);
    //    }

    @Override
    public void accept(QueuePipelineContext context) {
        var ctx = contextAdapter != null
                ? contextAdapter.apply(context)
                : context;
        stages.test(ctx);
    }

    //    private Future<?> initializeQueueAsync(QueueInitContext ctx) {
    //        var executor = Executors.newSingleThreadExecutor();
    //        try {
    //            return executor.submit(() -> {
    //                LogUtil.setMdcCrawlerId(ctx.getCrawlerContext().getId());
    //                Thread.currentThread().setName(ctx.getCrawlerContext().getId());
    //                LOG.info("Queuing start references asynchronously.");
    //                initializer.accept(ctx);
    //            });
    //        } finally {
    //            try {
    //                executor.shutdown();
    //                executor.awaitTermination(5, TimeUnit.SECONDS);
    //            } catch (InterruptedException e) {
    //                LOG.error("Reading of start references interrupted.", e);
    //                Thread.currentThread().interrupt();
    //            }
    //        }
    //    }
    //
    //    private Future<?> initializeQueueSync(QueueInitContext ctx) {
    //        LOG.info("Queuing start references synchronously.");
    //        initializer.accept(ctx);
    //        return CompletableFuture.completedFuture(null);
    //    }

    //    /**
    //     * Holds contextual objects necessary to initialize a crawler queue.
    //     */
    //    @Accessors(fluent = false)
    //    @AllArgsConstructor
    //    public static class QueueInitContext {
    //        @Getter
    //        private final CrawlerContext crawlerContext;
    //        @Getter
    //        private final boolean resuming;
    //        private final Consumer<CrawlDocContext> queuer;
    //
    //        public void queue(@NonNull String reference) {
    //            var rec = crawlerContext.newDocContext(reference);
    //            rec.setDepth(0);
    //            queue(rec);
    //        }
    //
    //        public void queue(@NonNull CrawlDocContext rec) {
    //            queuer.accept(rec);
    //        }
    //    }
}
