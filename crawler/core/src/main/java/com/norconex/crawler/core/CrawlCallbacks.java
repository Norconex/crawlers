/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlContext;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CrawlCallbacks {

    /**
     * Invoked after a command is initialized, but before it gets executed.
     */
    Consumer<CrawlContext> beforeCommand;
    /**
     * Invoked after a command has been executed, but before resources are
     * closed.
     */
    Consumer<CrawlContext> afterCommand;

    /**
     * Gives crawler implementations a chance to prepare before execution
     * of {@link CrawlTask_TO_MIGRATE} starts. Invoked right after the
     * {@link CrawlerEvent#TASK_RUN_BEGIN} event is fired.
     */
    Consumer<CrawlContext> beforeCrawlTask;

    /**
     * Gives crawler implementations a chance to do something right after
     * the {@link CrawlTask_TO_MIGRATE} is done processing its last reference, before all
     * task resources are shut down.
     * Invoked right after
     * {@link CrawlerEvent#TASK_RUN_END} (depending which of the two is
     * triggered).
     */
    Consumer<CrawlContext> afterCrawlTask;

    //MAYBE: are those used? Should they be?
    // Add those that are missing to ReferencesProcessor
    BiConsumer<CrawlContext, CrawlDoc> beforeDocumentProcessing;
    BiConsumer<CrawlContext, CrawlDoc> afterDocumentProcessing;

    //MAYBE: need those, or we can replace beforeDocumentFinalizing
    // (the only one used) with after processing?
    BiConsumer<CrawlContext, CrawlDoc> beforeDocumentFinalizing;
    BiConsumer<CrawlContext, CrawlDoc> afterDocumentFinalizing;

}
