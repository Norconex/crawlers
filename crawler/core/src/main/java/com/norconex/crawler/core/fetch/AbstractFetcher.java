/* Copyright 2022-2025 Norconex Inc.
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
import com.norconex.crawler.core.doc.operations.filter.FilterGroupResolver;
import com.norconex.crawler.core.doc.operations.filter.ReferenceFilter;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlContext;
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
 * @param <C> configuration type
 */
@Slf4j
@EqualsAndHashCode
@ToString
@XmlAccessorType(XmlAccessType.NONE)
public abstract class AbstractFetcher<C extends BaseFetcherConfig>
        implements Fetcher, EventListener<Event>, Configurable<C> {

    @Override
    public final boolean accept(@NonNull FetchRequest fetchRequest) {
        if (isAcceptedByReferenceFilters(fetchRequest)
                && acceptRequest(fetchRequest)) {
            LOG.debug("Fetcher {} ACCEPTED request '{}'.",
                    getClass().getSimpleName(), fetchRequest);
            return true;
        }
        LOG.debug("Fetcher {} REJECTED request '{}'.",
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
     *     (never {@code null})
     * @return <code>true</code> if accepted
     */
    protected boolean acceptRequest(@NonNull FetchRequest fetchRequest) {
        return true;
    }

    @Override
    public final void accept(Event event) {

        //TODO create init/destroy methods instead?

        if (event.is(CrawlerEvent.CRAWLER_CRAWL_BEGIN)) {
            fetcherStartup((CrawlContext) event.getSource());
        } else if (event.is(CrawlerEvent.CRAWLER_CRAWL_END)) {
            fetcherShutdown((CrawlContext) event.getSource());
        }
    }

    /**
     * Invoked once per fetcher instance, when the crawler starts.
     * Default implementation does nothing.
     * @param crawler crawler
     */
    protected void fetcherStartup(CrawlContext crawler) {
        //NOOP
    }

    /**
     * Invoked once per fetcher when the crawler ends.
     * Default implementation does nothing.
     * @param crawler crawler
     */
    protected void fetcherShutdown(CrawlContext crawler) {
        //NOOP
    }

    private boolean isAcceptedByReferenceFilters(
            @NonNull FetchRequest fetchRequest) {
        var ref = Optional.ofNullable(fetchRequest.getDoc())
                .map(Doc::getReference)
                .orElse(null);
        return FilterGroupResolver.<ReferenceFilter>builder()
                .filterResolver(f -> f.acceptReference(ref))
                .onAccepted(f -> LOG.debug(
                        "Fetcher {} ACCEPTED reference: '{}'. Filter={}",
                        getClass().getSimpleName(), ref, f))
                .onRejected(f -> LOG.debug(
                        "Fetcher {} REJECTED reference: '{}'. Filter={}",
                        getClass().getSimpleName(), ref, f))
                .onRejectedNoInclude(f -> LOG.debug(
                        "Fetcher {} REJECTED reference: '{}'. "
                                + "No 'include' filters matched.",
                        getClass().getSimpleName(), ref))
                .build()
                .accepts(getConfiguration().getReferenceFilters());
    }
}
