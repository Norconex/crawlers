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
package com.norconex.crawler.core.session;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.importer.ImporterConfig;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Base Collector configuration.
 * </p>
 *
 * <h3>XML Configuration</h3>
 * <p>
 * Subclasses inherit the following XML configuration items.
 * </p>
 *
 * {@nx.xml #collector
 * <workDir>
 *   (Directory where generated and temporary files are written.
 *    Defaults to "./work")
 * </workDir>
 * <eventListeners>
 *   <!-- Repeat as needed. -->
 *   <listener class="(EventListener implementation class name.)"/>
 * </eventListeners>
 * <maxConcurrentCrawlers>
 *   (Maximum number of crawlers that can run simultaneously.
 *    Only applicable when more than one crawler is configured.
 *    Defaults to -1, unlimited.)
 * </maxConcurrentCrawlers>
 * <crawlersStartInterval>
 *   (Millisecond interval between each crawlers start. Default starts them
 *    all at once.)
 * </crawlersStartInterval>
 * <maxMemoryPool>
 *   (Maximum number of bytes used for memory caching of documents data. E.g.,
 *    when processing documents. Defaults to 1 GB.)
 * </maxMemoryPool>
 * <maxMemoryInstance>
 *   (Maximum number of bytes used for memory caching of each individual
 *    documents document. Defaults to 100 MB.)
 * </maxMemoryInstance>
 *
 * <deferredShutdownDuration>
 *   (Optional amount of time to defer the collector shutdown when it is
 *    done executing. This is useful if you have external processes that
 *    need a bit of time to catch up. E.g.,: 10 seconds. Defaults to 0.)
 * </deferredShutdownDuration>
 *
 * <crawlerDefaults>
 *   <!-- All crawler options defined in a "crawler" section (except for
 *        the crawler "id") can be set here as default shared between
 *        multiple crawlers. Configuration blocks defined for a specific
 *        crawler always takes precedence. -->
 * </crawlerDefaults>
 * }
 * {@nx.xml
 * <crawlers>
 *   <!-- You need to define at least one crawler. -->
 *   <crawler id="(Unique identifier for this crawler)">
 *     <!-- Crawler settings -->
 *   </crawler>
 * </crawlers>
 * }
 *
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 */
@Data
@FieldNameConstants
@Slf4j
public class CrawlSessionConfig { // implements XMLConfigurable {

    /** Default relative directory where generated files are written. */
    public static final Path DEFAULT_WORK_DIR = Paths.get("./work");

    /**
     * Unique identifier for this crawling session configuration.
     * It is important the id of the session is unique amongst your all
     * your configured sessions.  This facilitates integration with different
     * systems and facilitates tracking.
     * @param id unique identifier
     * @return unique identifier
     */
    @SuppressWarnings("javadoc")
    private String id;

    /**
     * The base directory location where files generated during execution
     * will reside. When <code>null</code> the collector will use
     * <code>./work</code> at runtime.
     * @param workDir working directory path
     * @return working directory path
     */
    @SuppressWarnings("javadoc")
    private Path workDir = DEFAULT_WORK_DIR;

    /**
     * Maximum number of bytes used for memory caching of all reusable streams
     * at any given time, for faster processing. Defaults to 1 GB.
     * File-caching is used when the maximum is reached.
     * @param streamCachePoolSize
     *     maximum number of bytes for all reusable streams combined
     * @return maximum number of bytes for all reusable streams combined
     */
    @SuppressWarnings("javadoc")
    private long maxStreamCachePoolSize =
            ImporterConfig.DEFAULT_MAX_STREAM_CACHE_POOL_SIZE;

    /**
     * Maximum number of bytes used for memory caching of a single reusable
     * stream, for faster processing. Defaults to 100 MB. File-caching is
     * used when this maximum is reached for a single file, or when the
     * pool size has been reached.
     * @param streamCacheSize
     *     maximum number of bytes for a single reusable streams
     * @return maximum number of bytes for a single reusable stream
     */
    @SuppressWarnings("javadoc")
    private long maxStreamCacheSize =
            ImporterConfig.DEFAULT_MAX_STREAM_CACHE_SIZE;

    /**
     * The maximum number of crawlers that can be executed concurrently.
     * Default is <code>-1</code>, which means no maximum.
     * @param maxConcurrentCrawlers
     *     maximum number of crawlers to be executed concurrently
     * @return maximum number of crawlers to be executed concurrently
     */
    @SuppressWarnings("javadoc")
    private int maxConcurrentCrawlers = -1;

    /**
     * The amount of time between each concurrent crawlers are started.
     * Default is <code>null</code> (does not wait before launching concurrent
     * crawlers).
     * @param crawlersStartInterval amount of time
     * @return amount of time or <code>null</code>
     */
    @SuppressWarnings("javadoc")
    private Duration crawlersStartInterval;

    // see methods for javadoc
    @JsonProperty("crawlers")
    private final List<CrawlerConfig> crawlerConfigs = new ArrayList<>();
    private final List<EventListener<?>> eventListeners = new ArrayList<>();

    /**
     * The amount of time to defer the collector shutdown when it is
     * done executing. This is useful for giving external processes
     * with polling intervals enough time to grab the latest state of
     * the collector before it shuts down.  Default is zero (does not
     * wait to shutdown after completion).
     * @param deferredShutdownDuration duration
     * @return duration
     */
    @SuppressWarnings("javadoc")
    private Duration deferredShutdownDuration = Duration.ZERO;

    /**
     * Configurations for each crawlers to be executed together in a crawl
     * session.
     * @return crawler configuration list (never <code>null</code>)
     */
    public List<CrawlerConfig> getCrawlerConfigs() {
        return Collections.unmodifiableList(crawlerConfigs);
    }
    /**
     * Configurations for each crawlers to be executed together in a crawl
     * session.
     * @param crawlerConfigs crawler configuration list
     */
    public void setCrawlerConfigs(List<CrawlerConfig> crawlerConfigs) {
        CollectionUtil.setAll(this.crawlerConfigs, crawlerConfigs);
    }

    /**
     * Gets event listeners.
     * Those are considered additions to automatically
     * detected configuration objects implementing {@link EventListener}.
     * @return event listeners.
     */
    public List<EventListener<?>> getEventListeners() {
        return Collections.unmodifiableList(eventListeners);
    }
    /**
     * Sets event listeners.
     * Those are considered additions to automatically
     * detected configuration objects implementing {@link EventListener}.
     * @param eventListeners event listeners.
     */
    public void setEventListeners(List<EventListener<?>> eventListeners) {
        CollectionUtil.setAll(this.eventListeners, eventListeners);
    }
    /**
     * Adds event listeners.
     * Those are considered additions to automatically
     * detected configuration objects implementing {@link EventListener}.
     * @param eventListeners event listeners.
     */
    public void addEventListeners(List<EventListener<?>> eventListeners) {
        this.eventListeners.addAll(eventListeners);
    }
    /**
     * Adds a single event listener to already registered listeners.
     * @param eventListener event listener
     */
    public void addEventListener(EventListener<?> eventListener) {
        eventListeners.add(eventListener);
    }
    /**
     * Removes a single event listener from the list of registered listeners
     * if present.
     * @param eventListener event listener
     * @return <code>true</code> if the entity listener existed
     */
    public boolean removeEventListener(EventListener<?> eventListener) {
        return eventListeners.remove(eventListener);
    }
    /**
     * Clears all event listeners. The automatically
     * detected configuration objects implementing {@link EventListener}
     * are not cleared.
     */
    public void clearEventListeners() {
        eventListeners.clear();
    }

//    @Override
//    public void saveToXML(XML xml) {
//        xml.setAttribute(Fields.id, getId());
//        // we convert to string here or it will think it is a configurable
//        // class and will add a "class" attribute.
//        xml.addElement(Fields.crawlerConfigClass,
//                getCrawlerConfigClass().getName());
//        xml.addElement(Fields.workDir, getWorkDir());
//        xml.addElement(
//                Fields.maxConcurrentCrawlers, getMaxConcurrentCrawlers());
//        xml.addElement(
//                Fields.crawlersStartInterval, getCrawlersStartInterval());
//        xml.addElement(
//                Fields.maxStreamCachePoolSize, getMaxStreamCachePoolSize());
//        xml.addElement(Fields.maxStreamCacheSize, getMaxStreamCacheSize());
//        xml.addElementList(Fields.eventListeners, "listener", eventListeners);
//        xml.addElement(
//                "deferredShutdownDuration", getDeferredShutdownDuration());
//        xml.addElementList("crawlers", "crawler", getCrawlerConfigs());
//    }
//
//    @Override
//    public final void loadFromXML(XML xml) {
//        var crawlSessionId = xml.getString("@" + Fields.id, null);
//        if (StringUtils.isBlank(crawlSessionId)) {
//            throw new CrawlSessionException(
//                    "Crawl session id attribute is mandatory.");
//        }
//        setId(crawlSessionId);
//        crawlerConfigClass = xml.getClass(
//                Fields.crawlerConfigClass, getCrawlerConfigClass());
//        setWorkDir(xml.getPath(Fields.workDir, getWorkDir()));
//        setMaxConcurrentCrawlers(xml.getInteger(
//                Fields.maxConcurrentCrawlers, getMaxConcurrentCrawlers()));
//        setCrawlersStartInterval(xml.getDuration(
//                Fields.crawlersStartInterval, getCrawlersStartInterval()));
//        setMaxStreamCachePoolSize(xml.getDataSize(
//                Fields.maxStreamCachePoolSize, getMaxStreamCachePoolSize()));
//        setMaxStreamCacheSize(xml.getDataSize(
//                Fields.maxStreamCacheSize, getMaxStreamCacheSize()));
//        setEventListeners(xml.getObjectListImpl(EventListener.class,
//                Fields.eventListeners + "/listener", eventListeners));
//        setDeferredShutdownDuration(
//                xml.getDuration("deferredShutdownDuration"));
//
//        if (crawlerConfigClass != null) {
//            var cfgs = loadCrawlerConfigs(xml);
//            if (CollectionUtils.isNotEmpty(cfgs)) {
//                setCrawlerConfigs(cfgs);
//            }
//        }
//    }
//
//    List<CrawlerConfig> loadCrawlerConfigs(XML xml) {
//        try {
//            var crawlerDefaultsXML = xml.getXML("crawlerDefaults");
//            var crawlersXML = xml.getXMLList("crawlers/crawler");
//            List<CrawlerConfig> configs = new ArrayList<>();
//            for (XML crawlerXML : crawlersXML) {
//                CrawlerConfig config = crawlerConfigClass
//                        .getDeclaredConstructor().newInstance();
//                LOG.debug("Loading crawler config for type: {}",
//                        config.getClass());
//                if (crawlerDefaultsXML != null) {
//                    populateCrawlerConfig(config, crawlerDefaultsXML);
//                    LOG.debug("Crawler defaults loaded for new crawler.");
//                }
//                populateCrawlerConfig(config, crawlerXML);
//                configs.add(config);
//                LOG.debug("Crawler configuration loaded: {}", config.getId());
//            }
//            return configs;
//        } catch (Exception e) {
//            throw new CrawlSessionException(
//                    "Cannot load crawler configurations.", e);
//        }
//    }
//
//    void populateCrawlerConfig(CrawlerConfig config, XML xml) {
//        if (xml == null) {
//            LOG.warn("Passing a null configuration for {}, skipping.",
//                    config.getId());
//            return;
//        }
//
//        // grab id only if we are not populating default crawler settings
//        if (!"crawlerDefaults".equalsIgnoreCase(xml.getName())) {
//            var crawlerId = xml.getString("@id", null);
//            if (StringUtils.isBlank(crawlerId)) {
//                throw new CrawlSessionException(
//                        "Crawler ID is missing in configuration.");
//            }
//        }
//
//        xml.populate(config);
//    }
}
