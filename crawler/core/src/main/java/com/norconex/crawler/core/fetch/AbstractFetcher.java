/* Copyright 2022-2024 Norconex Inc.
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
package com.norconex.crawler.core.fetch;

import java.util.Optional;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.commands.crawl.task.operations.filter.FilterGroupResolver;
import com.norconex.crawler.core.commands.crawl.task.operations.filter.ReferenceFilter;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.importer.doc.Doc;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Base class implementing the {@link #accept(FetchRequest)} method
 * using reference filters to determine if this fetcher will accept to fetch
 * a document, in addition to whatever logic implementing classes may provide
 * by optionally overriding {@link #acceptRequest(FetchRequest)}
 * (which otherwise always return <code>true</code>).
 * It also offers methods to overwrite in order to react to crawler
 * startup and shutdown events.
 * </p>
 *
 * @param <T> fetcher request type
 * @param <R> fetcher response type
 * @param <C> configuration type
 */
@Slf4j
@EqualsAndHashCode
@ToString
@XmlAccessorType(XmlAccessType.NONE)
public abstract class AbstractFetcher<
        T extends FetchRequest,
        R extends FetchResponse,
        C extends BaseFetcherConfig>
        implements
        Fetcher<T, R>,
        EventListener<Event>,
        Configurable<C> {

    @Override
    public final boolean accept(@NonNull T fetchRequest) {
        if (isAcceptedByReferenceFilters(fetchRequest)
                && acceptRequest(fetchRequest)) {
            LOG.debug(
                    "Fetcher {} ACCEPTED request '{}'.",
                    getClass().getSimpleName(), fetchRequest);
            return true;
        }
        LOG.debug(
                "Fetcher {} REJECTED request '{}'.",
                getClass().getSimpleName(), fetchRequest);
        return false;
    }

    /**
     * Optional method for subclasses to override when in need for additional
     * acceptance logic beyond reference filters (which this abstract class
     * provides). This method is only invoked if there are no reference
     * filters configured on this class, or all of them accepts the
     * request document.
     * Default implementation does nothing (always return <code>true</code>).
     * @param fetchRequest the fetch request to evaluate
     *     (never <code>null</code>)
     * @return <code>true</code> if accepted
     */
    protected boolean acceptRequest(@NonNull T fetchRequest) {
        return true;
    }

    @Override
    public final void accept(Event event) {

        //TODO create init/destroy methods instead?

        // Here we rely on session startup instead of
        // crawler startup to avoid being invoked multiple
        // times (once for each crawler)

        //TODO, also handle CRAWLER_CRAWL_[BEGIN|END] if we ever need
        // fetcher on client side.

        if (event.is(CrawlerEvent.CRAWLER_CONTEXT_INIT_END)) {
            fetcherStartup((CrawlerContext) event.getSource());
        } else if (event.is(CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_BEGIN)) {
            fetcherShutdown((CrawlerContext) event.getSource());
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN)
                && Thread.currentThread().equals(
                        ((CrawlerEvent) event).getSubject())) {
            fetcherThreadBegin((CrawlerContext) event.getSource());
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_THREAD_END)
                && Thread.currentThread().equals(
                        ((CrawlerEvent) event).getSubject())) {
            fetcherThreadEnd((CrawlerContext) event.getSource());
        }
    }

    /**
     * Invoked once per fetcher instance, when the crawler starts.
     * Default implementation does nothing.
     * @param crawler crawler
     */
    protected void fetcherStartup(CrawlerContext crawler) {
        //NOOP
    }

    /**
     * Invoked once per fetcher when the crawler ends.
     * Default implementation does nothing.
     * @param crawler crawler
     */
    protected void fetcherShutdown(CrawlerContext crawler) {
        //NOOP
    }

    /**
     * Invoked each time a crawler begins a new crawler thread if that thread
     * is the current thread.
     * Default implementation does nothing.
     * @param crawler crawler
     */
    protected void fetcherThreadBegin(CrawlerContext crawler) {
        //NOOP
    }

    /**
     * Invoked each time a crawler ends an existing crawler thread if that
     * thread is the current thread.
     * Default implementation does nothing.
     * @param crawler crawler
     */
    protected void fetcherThreadEnd(CrawlerContext crawler) {
        //NOOP
    }

    private boolean isAcceptedByReferenceFilters(@NonNull T fetchRequest) {
        var ref = Optional.ofNullable(fetchRequest.getDoc())
                .map(Doc::getReference)
                .orElse(null);
        return FilterGroupResolver.<ReferenceFilter>builder()
                .filterResolver(f -> f.acceptReference(ref))
                .onAccepted(
                        f -> LOG.debug(
                                "Fetcher {} ACCEPTED reference: '{}'. Filter={}",
                                getClass().getSimpleName(), ref, f))
                .onRejected(
                        f -> LOG.debug(
                                "Fetcher {} REJECTED reference: '{}'. Filter={}",
                                getClass().getSimpleName(), ref, f))
                .onRejectedNoInclude(
                        f -> LOG.debug(
                                "Fetcher {} REJECTED reference: '{}'. "
                                        + "No 'include' filters matched.",
                                getClass().getSimpleName(), ref))
                .build()
                .accepts(getConfiguration().getReferenceFilters());
    }
}