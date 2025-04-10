/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.util.LogUtil;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Default queue initializer, feeding the queue from a mix of reference
 * enqueuers. Default enqueuers are:
 * </p>
 * <ul>
 *   <li>{@link ListRefEnqueuer} (list of references)</li>
 *   <li>{@link FileRefEnqueuer} (files containing references)</li>
 *   <li>{@link ProviderRefEnqueuer} (Java-based reference providers)</li>
 * </ul>
 * <p>
 * Those are configurable in {@link CrawlerConfig}.
 * </p>
 */
@Slf4j
@EqualsAndHashCode
public class QueueBootstrapper implements Predicate<CrawlerContext> {

    private final List<ReferenceEnqueuer> enqueuers = new ArrayList<>();

    public QueueBootstrapper() {
        enqueuers.add(new ListRefEnqueuer());
        enqueuers.add(new FileRefEnqueuer());
        enqueuers.add(new ProviderRefEnqueuer());
    }

    public QueueBootstrapper(
            List<ReferenceEnqueuer> enqueuers) {
        if (CollectionUtils.isNotEmpty(enqueuers)) {
            this.enqueuers.addAll(enqueuers);
        }
    }

    @Override
    public boolean test(CrawlerContext crawlerContext) {
        if (crawlerContext.isResuming()) {
            LOG.info("Unfinished previous crawl detected. Resuming...");
        } else {
            LOG.info("Queuing start references ({})...",
                    crawlerContext.getConfiguration().isStartReferencesAsync()
                            ? "asynchronously"
                            : "synchronously");
        }

        //--- Queue initial references ---------------------------------
        //TODO if we resume, shall we not queue again? What if it stopped
        // in the middle of initial queuing, then to be safe we have to
        // queue again and expect that those that were already processed
        // will simply be ignored (default behavior).
        // Consider persisting a flag that would tell us if we are resuming
        // with an incomplete queue initialization, or make initialization
        // more sophisticated so we can resume in the middle of it
        // (this last option would likely be very impractical).
        //        var initializer = crawlerContext.getSpec().queueInitializer();
        //
        //        if (initializer != null) {
        LOG.info("Queueing initial references...");
        var queueInitContext = new QueueBootstrapContext(
                crawlerContext,
                crawlerContext.isResuming(),
                docCtx -> queue(
                        new QueuePipelineContext(crawlerContext, docCtx)));

        var callback = (Consumer<QueueBootstrapContext>) ctx -> {
            var cnt = 0;
            for (var enqueuer : enqueuers) {
                cnt += enqueuer.enqueue(ctx);
            }
            if (LOG.isInfoEnabled()) {
                LOG.info("{} start URLs identified.",
                        NumberFormat.getNumberInstance().format(cnt));
            }
        };

        var cfg = crawlerContext.getConfiguration();
        if (cfg.isStartReferencesAsync()) {
            initializeQueueAsync(callback, queueInitContext);
        } else {
            initializeQueueSync(callback, queueInitContext);
        }

        crawlerContext.queueInitialized();
        return true;
    }

    private void queue(QueuePipelineContext context) {
        context.getCrawlerContext()
                .getPipelines()
                .getQueuePipeline()
                .accept(context);
    }

    private void initializeQueueAsync(
            Consumer<QueueBootstrapContext> callback, QueueBootstrapContext ctx) {
        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                LogUtil.setMdcCrawlerId(ctx.getCrawlerContext().getId());
                Thread.currentThread().setName(ctx.getCrawlerContext().getId());
                LOG.info("Queuing start references asynchronously.");
                callback.accept(ctx);
                ctx.getCrawlerContext().queueInitialized();
            });
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

    private void initializeQueueSync(
            Consumer<QueueBootstrapContext> callback, QueueBootstrapContext ctx) {
        LOG.info("Queuing start references synchronously.");
        callback.accept(ctx);
        ctx.getCrawlerContext().queueInitialized();
    }
}
