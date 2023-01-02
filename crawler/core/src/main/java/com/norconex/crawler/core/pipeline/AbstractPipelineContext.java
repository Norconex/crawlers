/* Copyright 2014-2022 Norconex Inc.
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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.pipeline.IPipelineStage;
import com.norconex.commons.lang.pipeline.Pipeline;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerCommitterService;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDocRecordService;

/**
 * Base {@link IPipelineStage} context for collector {@link Pipeline}s.
 */
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
        return crawler.getCrawlerConfig();
    }

    public CrawlDocRecordService getDocInfoService() {
        return crawler.getDocInfoService();
    }

    public CrawlerCommitterService getCommitterService() {
        return crawler.getCommitterService();
    }

    public EventManager getEventManager() {
        return crawler.getEventManager();
    }

    public void fire(CrawlerEvent event) {
        getEventManager().fire(event);
    }
    /**
     * Fires a crawler event with the current crawler as source.
     * @param eventName the event name
     */
    //TODO used?
    public void fire(String eventName) {
        fire(CrawlerEvent.builder()
                .name(eventName)
                .source(crawler)
                .build());
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this,
                ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
