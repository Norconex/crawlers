/* Copyright 2018-2021 Norconex Inc.
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
package com.norconex.collector.http.fetch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorEvent;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.filter.IReferenceFilter;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.IEventListener;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.filter.IOnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * <p>
 * Base class implementing the {@link #accept(Doc, HttpMethod)} method
 * using reference filters to determine if this fetcher will accept to fetch
 * a URL and delegating the HTTP method check to its own
 * {@link #accept(HttpMethod)} abstract method.
 * It also offers methods to overwrite in order to react to crawler
 * startup and shutdown events.
 * </p>
 * <h3>XML configuration usage:</h3>
 * Subclasses inherit this {@link IXMLConfigurable} configuration:
 *
 * {@nx.xml.usage #referenceFilters
 * <referenceFilters>
 *   <!-- multiple "filter" tags allowed -->
 *   <filter class="(any reference filter class)">
 *      (Refer to the documentation for the implementation of IReferenceFilter
 *       you are using here for usage details.)
 *   </filter>
 * </referenceFilters>
 * }
 *
 * <h4>Usage example:</h4>
 * <p>This filter example will restrict applying an HTTP Fetcher to URLs
 * ending with ".pdf".
 * </p>
 *
 * {@nx.xml.example
 * <referenceFilters>
 *   <filter class="ReferenceFilter" onMatch="exclude">
 *     <valueMatcher method="regex">https://example\.com/pdfs/.*</valueMatcher>
 *   </filter>
 * </referenceFilters>
 * }
 *
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public abstract class AbstractHttpFetcher implements
        IHttpFetcher, IXMLConfigurable, IEventListener<Event> {

    private static final Logger LOG =
            LoggerFactory.getLogger(AbstractHttpFetcher.class);

    private final List<IReferenceFilter> referenceFilters = new ArrayList<>();

    public AbstractHttpFetcher() {
        super();
    }

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
    public boolean accept(Doc doc, HttpMethod httpMethod) {
        if (accept(httpMethod)) {
            LOG.debug("Fetcher {} ACCEPTED HTTP method '{}' for {}.",
                    getClass().getSimpleName(), httpMethod, doc.getReference());
        } else {
            LOG.debug("Fetcher {} REJECTED HTTP method '{}' for {}.",
                    getClass().getSimpleName(), httpMethod, doc.getReference());
            return false;
        }
        return !isRejectedByReferenceFilters(doc);
    }

    /**
     * Whether the supplied HttpMethod is supported by this fetcher.
     * @param httpMethod the HTTP method
     * @return <code>true</code> if supported
     */
    protected abstract boolean accept(HttpMethod httpMethod);

    @Override
    public final void accept(Event event) {
    	// Here we rely on collector startup instead of
    	// crawler startup to avoid being invoked multiple
    	// times (once for each crawler)
    	if (event.is(CollectorEvent.COLLECTOR_RUN_BEGIN)) {
    		fetcherStartup((HttpCollector) event.getSource());
    	} else if (event.is(CollectorEvent.COLLECTOR_RUN_END)) {
    		fetcherShutdown((HttpCollector) event.getSource());
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN)
                && Thread.currentThread().equals(
                        ((CrawlerEvent) event).getSubject())) {
            fetcherThreadBegin((HttpCrawler) event.getSource());
        } else if (event.is(CrawlerEvent.CRAWLER_RUN_THREAD_END)
                && Thread.currentThread().equals(
                        ((CrawlerEvent) event).getSubject())) {
            fetcherThreadEnd((HttpCrawler) event.getSource());
    	}
    }


    /**
     * Invoked once per fetcher instance, when the collector starts.
     * Default implementation does nothing.
     * @param collector collector
     */
	protected void fetcherStartup(HttpCollector collector) {
        //NOOP
    }
    /**
     * Invoked once per fetcher when the collector ends.
     * Default implementation does nothing.
     * @param collector collector
     */
	protected void fetcherShutdown(HttpCollector collector) {
        //NOOP
    }

    /**
     * Invoked each time a crawler begins a new crawler thread if that thread
     * is the current thread.
     * Default implementation does nothing.
     * @param crawler crawler
     */
    protected void fetcherThreadBegin(HttpCrawler crawler) {
        //NOOP
    }
    /**
     * Invoked each time a crawler ends an existing crawler thread if that
     * thread is the current thread.
     * Default implementation does nothing.
     * @param crawler crawler
     */
    protected void fetcherThreadEnd(HttpCrawler crawler) {
        //NOOP
    }


    // return true if reference is rejected
    //TODO make reference filters always implement "onMatch" and move
    // this somewhere more generic?
    private boolean isRejectedByReferenceFilters(Doc doc) {
        String ref = doc.getReference();
        boolean hasIncludes = false;
        boolean atLeastOneIncludeMatch = false;
        for (IReferenceFilter filter : referenceFilters) {
            boolean accepted = filter.acceptReference(ref);

            // Deal with includes
            if (isIncludeFilter(filter)) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }

            // Deal with exclude and non-OnMatch filters
            if (accepted) {
                LOG.debug("Fetcher {} ACCEPTED reference: '{}'. Filter={}",
                        getClass().getSimpleName(), ref, filter);
            } else {
                LOG.debug("Fetcher {} REJECTED reference: '{}'. Filter={}",
                        getClass().getSimpleName(), ref, filter);
                return true;
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            LOG.debug("Fetcher {} REJECTED reference: '{}'. "
                  + "No 'include' filters matched.",
                    getClass().getSimpleName(), ref);
            return true;
        }
        return false;
    }

    private static boolean isIncludeFilter(IReferenceFilter filter) {
        return filter instanceof IOnMatchFilter
                && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
    }

    @Override
    public final void loadFromXML(XML xml) {
        loadHttpFetcherFromXML(xml);
        setReferenceFilters(xml.getObjectListImpl(IReferenceFilter.class,
                "referenceFilters/filter", referenceFilters));
    }
    @Override
    public final void saveToXML(XML xml) {
        saveHttpFetcherToXML(xml);
        xml.addElementList("referenceFilters", "filter", referenceFilters);
    }

    protected abstract void loadHttpFetcherFromXML(XML xml);
    protected abstract void saveHttpFetcherToXML(XML xml);

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
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}