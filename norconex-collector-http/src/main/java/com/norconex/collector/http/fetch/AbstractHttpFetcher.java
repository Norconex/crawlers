/* Copyright 2018-2019 Norconex Inc.
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

import static com.norconex.collector.core.crawler.CrawlerEvent.CRAWLER_RUN_BEGIN;
import static com.norconex.collector.core.crawler.CrawlerEvent.CRAWLER_RUN_END;
import static com.norconex.collector.core.crawler.CrawlerEvent.CRAWLER_STOP_END;

import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.IEventListener;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Base class implementing the {@link #accept(HttpDocument)} method
 * using restrictions and offering methods to overwrite for crawler startup
 * and shutdown.
 * </p>
 * <h3>XML configuration usage:</h3>
 * Subclasses inherit this {@link IXMLConfigurable} configuration:
 *
 * <pre>
 *  &lt;restrictions&gt;
 *      &lt;restrictTo caseSensitive="[false|true]"
 *              field="(name of metadata field name to match)"&gt;
 *          (regular expression of value to match)
 *      &lt;/restrictTo&gt;
 *      &lt;!-- multiple "restrictTo" tags allowed (only one needs to match) --&gt;
 *  &lt;/restrictions&gt;
 * </pre>
 *
 * <h4>Usage example:</h4>
 * <p>This example will restrict applying an HTTP Fetcher to URLs ending with
 * ".pdf".
 * </p>
 * <pre>
 *  ...
 *  &lt;restrictions&gt;
 *      &lt;restrictTo field="document.reference"&gt;.*\.pdf$&lt;/restrictTo&gt;
 *  &lt;/restrictions&gt;
 *  ...
 * </pre>
 *
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public abstract class AbstractHttpFetcher implements
        IHttpFetcher, IXMLConfigurable, IEventListener<CrawlerEvent<Crawler>> {

    private final PropertyMatchers restrictions = new PropertyMatchers();

    public AbstractHttpFetcher() {
        super();
    }

    public PropertyMatchers getRestrictions() {
        return restrictions;
    }

    @Override
    public boolean accept(HttpDocument doc) {
        return restrictions.test(doc.getMetadata());
    }

    @Override
    public final void accept(CrawlerEvent<Crawler> event) {
        if (isCrawlerStartup(event)) {
            crawlerStartup(event);
        } else if (isCrawlerShutdown(event)) {
            crawlerShutdown(event);
        }
    }



    //TODO remove these empty methods given it can be added as needed?
    public final boolean isCrawlerStartup(Event<?> event) {
        return event instanceof CrawlerEvent
                && event.is(CRAWLER_RUN_BEGIN);
    }
    public final boolean isCrawlerShutdown(Event<?> event) {
        return event instanceof CrawlerEvent
                && event.is(CRAWLER_RUN_END, CRAWLER_STOP_END);
    }

    /**
     * Invoked when a crawler is either started or resumed.
     * Default implementation does nothing.
     * @param event crawler event
     */
    protected void crawlerStartup(CrawlerEvent<Crawler> event) {
        //NOOP
    }
    /**
     * Invoked when a crawler is either finished or stopped.
     * Default implementation does nothing.
     * @param event crawler event
     */
    protected void crawlerShutdown(CrawlerEvent<Crawler> event) {
        //NOOP
    }

    @Override
    public final void loadFromXML(XML xml) {
        loadHttpFetcherFromXML(xml);
        List<XML> nodes = xml.getXMLList("restrictions/restrictTo");
        for (XML node : nodes) {
            restrictions.add(PropertyMatcher.loadFromXML(node));
        }
    }
    @Override
    public final void saveToXML(XML xml) {
        saveHttpFetcherToXML(xml);
        if (!restrictions.isEmpty()) {
            XML restrictsXML = xml.addElement("restrictions");
            for (PropertyMatcher restrict : restrictions) {
                PropertyMatcher.saveToXML(
                        restrictsXML.addElement("restrictTo"), restrict);
            }
        }
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