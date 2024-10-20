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
package com.norconex.crawler.core;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.event.CrawlerEvent;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CrawlerCallbacks {

    /**
     * Gives crawler implementations a chance to prepare before execution
     * starts. Invoked right after the
     * {@link CrawlerEvent#CRAWLER_RUN_BEGIN} event is fired.
     * This method is different than the {@link #initCrawler()} method,
     * which is invoked for any type of actions where as this one is only
     * invoked before an effective request for crawling.
     */
    Consumer<Crawler> beforeCrawlerExecution;

    /**
     * Gives crawler implementations a chance to do something right after
     * the crawler is done processing its last reference, before all resources
     * are shut down.
     * Invoked right after {@link CrawlerEvent#CRAWLER_STOP_END} or
     * {@link CrawlerEvent#CRAWLER_RUN_END} (depending which of the two is
     * triggered).
     */
    Consumer<Crawler> afterCrawlerExecution;

    //MAYBE: are those used? Should they be?
    // Add those that are missing to ReferencesProcessor
    BiConsumer<Crawler, CrawlDoc> beforeDocumentProcessing;
    BiConsumer<Crawler, CrawlDoc> afterDocumentProcessing;

    //MAYBE: need those, or we can replace beforeDocumentFinalizing
    // (the only one used) with after processing?
    BiConsumer<Crawler, CrawlDoc> beforeDocumentFinalizing;
    BiConsumer<Crawler, CrawlDoc> afterDocumentFinalizing;

}
