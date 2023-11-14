/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.crawler.core.pipeline;

import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocRecordService;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Base context object for a crawler pipelines.
 */
@EqualsAndHashCode
@ToString
public abstract class AbstractPipelineContext {

    private final Crawler crawler;

    /**
     * Constructor.
     * @param crawler the crawler
     */
    protected AbstractPipelineContext(Crawler crawler) {
        this.crawler = crawler;
    }

    public Crawler getCrawler() {
        return crawler;
    }

    public CrawlerConfig getConfig() {
        return crawler.getConfiguration();
    }

    public CrawlDocRecordService getDocRecordService() {
        return crawler.getDocRecordService();
    }

    public CommitterService<CrawlDoc> getCommitterService() {
        return crawler.getCommitterService();
    }

    public EventManager getEventManager() {
        return crawler.getEventManager();
    }

    public void fire(CrawlerEvent event) {
        getEventManager().fire(event);
    }
}
