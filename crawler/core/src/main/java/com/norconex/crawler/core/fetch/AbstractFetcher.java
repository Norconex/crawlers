/* Copyright 2022-2022 Norconex Inc.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.filter.IReferenceFilter;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionEvent;
import com.norconex.importer.handler.filter.FilterGroupResolver;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Base class implementing the {@link #accept(IFetchRequest)} method
 * using reference filters to determine if this fetcher will accept to fetch
 * a document, in addition to whatever logic implementing classes may provide
 * by optionally overriding {@link #acceptRequest(IFetchRequest)}
 * (which otherwise always return <code>true</code>).
 * It also offers methods to overwrite in order to react to crawler
 * startup and shutdown events.
 * </p>
 * <h3>XML configuration usage:</h3>
 * Subclasses inherit this {@link XMLConfigurable} configuration:
 *
 * {@nx.xml.usage #referenceFilters
 * <referenceFilters>
 *   <!-- multiple "filter" tags allowed -->
 *   <filter class="(any reference filter class)">
 *      (Restrict usage of this fetcher to matching reference filters.
 *       Refer to the documentation for the IReferenceFilter implementation
 *       you are using here for usage details.)
 *   </filter>
 * </referenceFilters>
 * }
 *
 * <h4>Usage example:</h4>
 * <p>
 * This XML snippet is an example of filter that restricts the application of
 * this Fetcher to references ending with ".pdf".
 * </p>
 *
 * {@nx.xml.example
 * <referenceFilters>
 *   <filter class="ReferenceFilter" onMatch="exclude">
 *     <valueMatcher method="regex">.*\.pdf$</valueMatcher>
 *   </filter>
 * </referenceFilters>
 * }
 *
 * @param <T> fetcher request type
 * @param <R> fetcher response type
 */
@Slf4j
@EqualsAndHashCode
@ToString
public abstract class AbstractFetcher
        <T extends IFetchRequest, R extends IFetchResponse> implements
                IFetcher<T, R>, XMLConfigurable, EventListener<Event> {

    private final List<IReferenceFilter> referenceFilters = new ArrayList<>();

    /**
     * Gets reference filters
     * @return reference filters
     */
    public List<IReferenceFilter> getReferenceFilters() {
        return Collections.unmodifiableList(referenceFilters);
    }
    /**
     * Sets reference filters.
     * @param referenceFilters reference filters to set
     */
    public void setReferenceFilters(IReferenceFilter... referenceFilters) {
        setReferenceFilters(Arrays.asList(referenceFilters));
    }
    /**
     * Sets reference filters.
     * @param referenceFilters the referenceFilters to set
     */
    public void setReferenceFilters(List<IReferenceFilter> referenceFilters) {
        CollectionUtil.setAll(this.referenceFilters, referenceFilters);
    }

    @Override
    public final boolean accept(@NonNull T fetchRequest) {
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
     *     (never <code>null</code>)
     * @return <code>true</code> if accepted
     */
    protected boolean acceptRequest(@NonNull T fetchRequest) {
        return true;
    }

    @Override
    public final void accept(Event event) {
    	// Here we rely on collector startup instead of
    	// crawler startup to avoid being invoked multiple
    	// times (once for each crawler)
    	if (event.is(CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN)) {
    		fetcherStartup((CrawlSession) event.getSource());
    	} else if (event.is(CrawlSessionEvent.CRAWLSESSION_RUN_END)) {
    		fetcherShutdown((CrawlSession) event.getSource());
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN)
                && Thread.currentThread().equals(
                        ((CrawlerEvent) event).getSubject())) {
            fetcherThreadBegin((Crawler) event.getSource());
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_THREAD_END)
                && Thread.currentThread().equals(
                        ((CrawlerEvent) event).getSubject())) {
            fetcherThreadEnd((Crawler) event.getSource());
    	}
    }


    /**
     * Invoked once per fetcher instance, when the collector starts.
     * Default implementation does nothing.
     * @param collector collector
     */
	protected void fetcherStartup(CrawlSession collector) {
        //NOOP
    }
    /**
     * Invoked once per fetcher when the collector ends.
     * Default implementation does nothing.
     * @param collector collector
     */
	protected void fetcherShutdown(CrawlSession collector) {
        //NOOP
    }

    /**
     * Invoked each time a crawler begins a new crawler thread if that thread
     * is the current thread.
     * Default implementation does nothing.
     * @param crawler crawler
     */
    protected void fetcherThreadBegin(Crawler crawler) {
        //NOOP
    }
    /**
     * Invoked each time a crawler ends an existing crawler thread if that
     * thread is the current thread.
     * Default implementation does nothing.
     * @param crawler crawler
     */
    protected void fetcherThreadEnd(Crawler crawler) {
        //NOOP
    }


    private boolean isAcceptedByReferenceFilters(T fetchRequest) {
        var ref = fetchRequest.getDoc().getReference();
        return FilterGroupResolver.<IReferenceFilter>builder()
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
            .accepts(referenceFilters);
    }

    @Override
    public final void loadFromXML(XML xml) {
        loadFetcherFromXML(xml);
        setReferenceFilters(xml.getObjectListImpl(IReferenceFilter.class,
                "referenceFilters/filter", referenceFilters));
    }
    @Override
    public final void saveToXML(XML xml) {
        saveFetcherToXML(xml);
        xml.addElementList("referenceFilters", "filter", referenceFilters);
    }

    protected abstract void loadFetcherFromXML(XML xml);
    protected abstract void saveFetcherToXML(XML xml);
}