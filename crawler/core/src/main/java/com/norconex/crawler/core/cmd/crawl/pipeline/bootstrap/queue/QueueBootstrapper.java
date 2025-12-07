/* Copyright 2023-2025 Norconex Inc.
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

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrapper;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.util.LogUtil;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Default queue initializer, feeding the queue from a mix of reference
 * enqueuers. Default enqueuers are:
 * </p>
 * <ul>
 *   <li>{@link RefListEnqueuer} (list of references)</li>
 *   <li>{@link RefFileEnqueuer} (files containing references)</li>
 *   <li>{@link RefProviderEnqueuer} (Java-based reference providers)</li>
 * </ul>
 * <p>
 * Those are configurable in {@link CrawlConfig}.
 * </p>
 */
@Slf4j
@EqualsAndHashCode
//TODO XXX rename this if no longer a "bootstrapper" as we are moving the logic back to pipeline
public class QueueBootstrapper implements CrawlBootstrapper {

    //TODO XXX ******** make summary about crawl run, just total process for session? Or show delta? ***********

    private final List<ReferenceEnqueuer> enqueuers = new ArrayList<>();

    public QueueBootstrapper() {
        enqueuers.add(new RefListEnqueuer());
        enqueuers.add(new RefFileEnqueuer());
        enqueuers.add(new RefProviderEnqueuer());
    }

    public QueueBootstrapper(
            List<ReferenceEnqueuer> enqueuers) {
        if (CollectionUtils.isNotEmpty(enqueuers)) {
            this.enqueuers.addAll(enqueuers);
        }
    }

    @Override
    public void bootstrap(CrawlSession session) {
        var crawlContext = session.getCrawlContext();
        if (session.isResumed()) {
            LOG.info("Unfinished previous crawl detected. Resuming...");
        } else {
            LOG.info("Queueing start references ({})...",
                    crawlContext.getCrawlConfig().isStartReferencesAsync()
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
        LOG.info("Queueing initial references...");
        var queueInitContext = new QueueBootstrapContext(
                session,
                docCtx -> queue(new QueuePipelineContext(session, docCtx)));

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

        var cfg = crawlContext.getCrawlConfig();
        if (cfg.isStartReferencesAsync()) {
            initializeQueueAsync(callback, queueInitContext);
        } else {
            initializeQueueSync(callback, queueInitContext);
        }
    }

    private void queue(QueuePipelineContext context) {
        context.getCrawlSession()
                .getCrawlContext()
                .getDocPipelines()
                .getQueuePipeline()
                .accept(context);
    }

    private void initializeQueueAsync(
            Consumer<QueueBootstrapContext> callback,
            QueueBootstrapContext ctx) {
        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                LogUtil.setMdcCrawlerId(ctx.getCrawlSession().getCrawlerId());
                Thread.currentThread()
                        .setName(ctx.getCrawlSession().getCrawlerId());
                callback.accept(ctx);
                ctx.getCrawlSession().setStartRefsQueueingComplete(true);
            });
        } finally {
            try {
                executor.shutdown();
                // Reduced from 5s to 2s for faster shutdown
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.error("Reading of start references interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private void initializeQueueSync(
            Consumer<QueueBootstrapContext> callback,
            QueueBootstrapContext ctx) {
        callback.accept(ctx);
        ctx.getCrawlSession().setStartRefsQueueingComplete(true);
    }
}
