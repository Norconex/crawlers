/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.core.tasks.crawl.pipelines.queue.stages;

import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.tasks.crawl.operations.filter.ReferenceFilter;
import com.norconex.crawler.core.tasks.crawl.pipelines.OnMatchFiltersResolver;
import com.norconex.crawler.core.tasks.crawl.pipelines.queue.QueuePipelineContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Common pipeline stage for filtering references.
 */
@Slf4j
public class ReferenceFiltersStage implements Predicate<QueuePipelineContext> {

    private final String type;

    public ReferenceFiltersStage() {
        this(null);
    }

    public ReferenceFiltersStage(String type) {
        this.type = type;
    }

    @Override
    public boolean test(QueuePipelineContext ctx) {
        var msgSuffix = StringUtils.isBlank(type)
                ? ""
                : " (" + StringUtils.trimToEmpty(type) + ")";

        return OnMatchFiltersResolver
                .<String, ReferenceFilter>builder()
                .subject(ctx.getDocContext().getReference())
                .filters(ctx.getCrawlerContext().getConfiguration()
                        .getReferenceFilters())
                .predicate((s, f) -> f.acceptReference(s))
                .onRejected((f, msg) -> {
                    LOG.debug(
                            "REJECTED reference{}: {} Filter={}",
                            msgSuffix, ctx.getDocContext().getReference(), f);
                    ctx.getCrawlerContext().fire(
                            CrawlerEvent.builder()
                                    .name(CrawlerEvent.REJECTED_FILTER)
                                    .source(ctx.getCrawlerContext())
                                    .docContext(ctx.getDocContext())
                                    .subject(f)
                                    .message(msg + msgSuffix)
                                    .build());
                    ctx.getDocContext().setState(CrawlDocState.REJECTED);
                })
                .build()
                .isAccepted();
    }
}
