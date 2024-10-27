/* Copyright 2014-2024 Norconex Inc.
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.norconex.committer.core.Committer;
import com.norconex.commons.lang.bean.jackson.JsonXmlCollection;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.event.listeners.StopCrawlerOnMaxEventListener;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridConnector;
import com.norconex.crawler.core.tasks.crawl.operations.DocumentConsumer;
import com.norconex.crawler.core.tasks.crawl.operations.checksum.DocumentChecksummer;
import com.norconex.crawler.core.tasks.crawl.operations.checksum.MetadataChecksummer;
import com.norconex.crawler.core.tasks.crawl.operations.checksum.impl.Md5DocumentChecksummer;
import com.norconex.crawler.core.tasks.crawl.operations.filter.DocumentFilter;
import com.norconex.crawler.core.tasks.crawl.operations.filter.MetadataFilter;
import com.norconex.crawler.core.tasks.crawl.operations.filter.ReferenceFilter;
import com.norconex.crawler.core.tasks.crawl.operations.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.tasks.crawl.operations.spoil.impl.GenericSpoiledReferenceStrategizer;
import com.norconex.crawler.core.tasks.crawl.pipelines.queue.ReferencesProvider;
import com.norconex.importer.ImporterConfig;

import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * <p>
 * Base Crawler configuration. Crawlers usually read this configuration upon
 * starting up. While not always enforced, once execution has started, it
 * should be considered immutable to avoid unexpected behaviors.
 * </p>
 * <p>
 * Concrete implementations inherit the following XML configuration
 * options (typically within a <code>&lt;crawler&gt;</code> tag):
 * </p>
 */
@Data
@Accessors(chain = true)
@FieldNameConstants
public class CrawlerConfig {

    /** Default relative directory where generated files are written. */
    public static final Path DEFAULT_WORKDIR = Paths.get("./work");

    public enum OrphansStrategy {
        /**
         * Processing orphans tries to obtain and process them again,
         * normally.
         */
        PROCESS,
        /**
         * Deleting orphans sends them to the Committer for deletions and
         * they are removed from the internal reference cache.
         */
        DELETE,
        /**
         * Ignoring orphans effectively does nothing with them
         * (not deleted, not processed).
         */
        IGNORE
    }

    public static final Duration DEFAULT_IDLE_PROCESSING_TIMEOUT =
            Duration.ofMinutes(10);
    public static final Duration DEFAULT_MIN_PROGRESS_LOGGING_INTERVAL =
            Duration.ofSeconds(30);

    //--- Properties -----------------------------------------------------------

    /**
     * The crawler unique identifier.
     * Using usual names is perfectly fine (non-alphanumeric characters are OK).
     * It is important for this crawler ID to be unique amongst your
     * crawlers in the same crawl session. On top of avoiding conflicts,
     * it facilitates integration with different systems and facilitates
     * tracking.
     */
    @JsonProperty(required = true)
    private String id;

    @JsonXmlCollection(entryName = "ref")
    private final List<String> startReferences = new ArrayList<>();
    @JsonXmlCollection(entryName = "file")
    private final List<Path> startReferencesFiles = new ArrayList<>();
    @JsonXmlCollection(entryName = "provider")
    private final List<ReferencesProvider> startReferencesProviders =
            new ArrayList<>();

    /**
     * The base directory location where files generated during execution
     * will reside. When <code>null</code> the collector will use
     * <code>./work</code>, relative to the execution "current" directory.
     */
    private Path workDir = DEFAULT_WORKDIR;

    /**
     * Maximum number of bytes used for memory caching of all reusable streams
     * combined, at any given time, for faster processing. Defaults to 1 GB.
     * File-caching is used when the maximum is reached.
     */
    private long maxStreamCachePoolSize =
            ImporterConfig.DEFAULT_MAX_STREAM_CACHE_POOL_SIZE;

    /**
     * Maximum number of bytes used for memory caching of a single reusable
     * stream, for faster processing. Defaults to 100 MB. File-caching is
     * used when this maximum is reached for a single file, or when the
     * pool maximum size has been reached.
     */
    private long maxStreamCacheSize =
            ImporterConfig.DEFAULT_MAX_STREAM_CACHE_SIZE;

    /**
     * The amount of time to defer the crawler shutdown when it is
     * done executing. This is useful for giving external processes
     * with polling intervals enough time to grab the latest state of
     * the collector before it shuts down.  Default is zero (does not
     * wait to shutdown after completion).
     */
    private Duration deferredShutdownDuration = Duration.ZERO;

    /**
     * The Grid Connector.
     */
    private GridConnector gridConnector = new IgniteGridConnector(); //new LocalGridConnector();

    /**
     * Whether the start references should be loaded asynchronously. When
     * <code>true</code>, the crawler will start processing the start
     * references in one or more separate threads as they are added to the
     * queue (as opposed to wait for queue initialization to be complete).
     * While this may speed up crawling, it may have an unexpected effect on
     * accuracy of {@link CrawlDocMetadata#DEPTH}. Use of this option is only
     * recommended when start references take a significant time to load.
     */
    private boolean startReferencesAsync;

    /**
     * The maximum number of threads a crawler can use. Default is 2.
     */
    @Min(1)
    private int numThreads = 2;

    /**
     * <p>
     * The maximum number of documents that can be processed before stopping.
     * Not all processed documents make it to your Committers
     * as some can be rejected.
     * </p>
     * <p>
     * In multi-threaded or clustered environments, the actual number
     * of documents processed may be a bit higher than the specified
     * maximum due to concurrency.
     * Upon reaching the configured maximum, the crawler will finish with
     * its documents actively being processed before stopping.
     * </p>
     * <p>
     * Reaching the maximum value will stop the crawler but it will not
     * otherwise consider the crawler session "complete", but rather
     * on "pause".  On next run, the crawler will resume the same session,
     * processing an additional number of documents up to the maximum
     * specified.
     * This maximum allows crawling one or more sources
     * in chunks, processing a maximum number of documents each time.
     * When the session fully completes, the next run will start a new
     * crawl session. To prevent resuming a partial crawl session,
     * explicitly clean the crawl store first.
     * </p>
     * <p>
     * For more control on what events may stop the crawler, consider using
     * configuring a {@link StopCrawlerOnMaxEventListener}.
     * </p>
     * <p>
     * Default is -1 (unlimited).
     * </p>
     */
    private int maxDocuments = -1;

    /**
     * The maximum depth the crawler should go. The exact definition of depth
     * is crawler-specific. Examples: levels of sub-directories,
     * number of URL clicks to reach a page, etc. Refer to specific crawler
     * implementation for details. Default is -1 (unlimited).
     */
    private int maxDepth = -1;

    /**
     * The maximum amount of time to wait before shutting down an inactive
     * crawler thread.
     * A document taking longer to process than the specified timeout
     * when no other thread are available to process remaining documents
     * is also considered "inactive". Default is
     * {@value #DEFAULT_IDLE_PROCESSING_TIMEOUT}. A <code>null</code>
     * value means no timeouts.
     */
    private Duration idleTimeout;

    /**
     * Minimum amount of time to wait between each logging of crawling
     * progress. Minimum value is 1 second.
     * Default value is {@value #DEFAULT_MIN_PROGRESS_LOGGING_INTERVAL}.
     * A <code>null</code> value or a value below 1 second disables progress
     * logging.
     */
    private Duration minProgressLoggingInterval =
            DEFAULT_MIN_PROGRESS_LOGGING_INTERVAL;

    /**
     * <p>The strategy to adopt when there are orphans.  Orphans are
     * references that were processed in a previous run, but were not in the
     * current run.  In other words, they are leftovers from a previous run
     * that were not re-encountered in the current.
     * </p><p>
     * Unless explicitly stated otherwise by a crawler implementation, the
     * default strategy is to <code>PROCESS</code> orphans.
     * Setting a <code>null</code> value is the same as setting
     * <code>IGNORE</code>.
     * </p><p>
     * <b>Be careful:</b> Setting the orphan strategy to <code>DELETE</code>
     * is NOT recommended in most cases. There are times when a temporary
     * failure such as a network outage or a web page timing out, may cause
     * some documents not to be crawled. When this happens, unreachable
     * documents would be considered "orphans" and be deleted while under
     * normal circumstances, they should be kept.  Re-processing them
     * (default), is usually the safest approach to confirm they still
     * exist before deleting or updating them.
     * </p>
     */
    private OrphansStrategy orphansStrategy = OrphansStrategy.PROCESS;

    private final List<Class<? extends Exception>> stopOnExceptions =
            new ArrayList<>();

    private final List<ReferenceFilter> referenceFilters = new ArrayList<>();
    private final List<MetadataFilter> metadataFilters = new ArrayList<>();
    private final List<DocumentFilter> documentFilters = new ArrayList<>();
    private final List<DocumentConsumer> preImportConsumers =
            new ArrayList<>();
    private final List<DocumentConsumer> postImportConsumers =
            new ArrayList<>();

    /**
     * The metadata checksummer.
     * Metadata checksum generation is disabled when <code>null</code>.
     */
    private MetadataChecksummer metadataChecksummer;

    /**
     * The Importer module configuration.
     */
    @JsonProperty("importer")
    private ImporterConfig importerConfig = new ImporterConfig();

    @JsonProperty("committers")
    @JacksonXmlElementWrapper(localName = "committers")
    @JacksonXmlProperty(localName = "committer")
    private final List<Committer> committers = new ArrayList<>();

    /**
     * Whether to turn ON deduplication based on metadata checksum.
     * To enable, {@link #getMetadataChecksummer()} must not return
     * <code>null</code>.
     * Not recommended unless you know for sure your metadata
     * checksum is acceptably unique.
     */
    private boolean metadataDeduplicate;

    /**
     * Whether to turn ON deduplication based on document checksum.
     * To enable, {@link #getDocumentChecksummer()} must not return
     * <code>null</code>.
     * Not recommended unless you know for sure your document
     * checksum is acceptably unique.
     */
    private boolean documentDeduplicate;

    /**
     * The document checksummer. Document checksum generation is disabled
     * when <code>null</code>.
     */
    private DocumentChecksummer documentChecksummer =
            new Md5DocumentChecksummer();

    /**
     * The spoiled state strategy resolver. A spoiled document is one that
     * was crawled properly before but on a subsequent crawl, it can no longer
     * be crawled for whatever reason (not found, bad status, server error,
     * etc.).
     */
    private SpoiledReferenceStrategizer spoiledReferenceStrategizer =
            new GenericSpoiledReferenceStrategizer();

    private final List<EventListener<?>> eventListeners = new ArrayList<>();

    private FetchDirectiveSupport metadataFetchSupport =
            FetchDirectiveSupport.DISABLED;
    private FetchDirectiveSupport documentFetchSupport =
            FetchDirectiveSupport.REQUIRED;

    /**
     * One or more fetchers responsible for obtaining documents and their
     * metadata from a source.
     */
    private final List<Fetcher<?, ?>> fetchers = new ArrayList<>();

    /**
     * The maximum number of times a fetcher will re-attempt fetching
     * a resource in case of failures.  Default is zero (won't retry).
     */
    private int fetchersMaxRetries;

    /**
     * How long to wait before a failing fetcher re-attempts fetching
     * a resource in case of failures (in milliseconds).
     * Default is zero (no delay).
     */
    private Duration fetchersRetryDelay;

    //--- List Accessors -------------------------------------------------------

    /**
     * Gets the references to initiate crawling from.
     * @return start references (never <code>null</code>)
     */
    public List<String> getStartReferences() {
        return Collections.unmodifiableList(startReferences);
    }

    /**
     * Sets the references to initiate crawling from.
     * @param startReferences start references
     * @return this
     */
    public CrawlerConfig setStartReferences(List<String> startReferences) {
        CollectionUtil.setAll(this.startReferences, startReferences);
        return this;
    }

    /**
     * Gets the file paths of seed files containing references to be used as
     * start references.  Files are expected to have one reference per line.
     * Blank lines and lines starting with # (comment) are ignored.
     * @return file paths of seed files containing references
     *         (never <code>null</code>)
     */
    public List<Path> getStartReferencesFiles() {
        return Collections.unmodifiableList(startReferencesFiles);
    }

    /**
     * Sets the file paths of seed files containing references to be used as
     * start references.  Files are expected to have one reference per line.
     * Blank lines and lines starting with # (comment) are ignored.
     * @param startReferencesFiles file paths of seed files containing
     *     references
     * @return this
     */
    public CrawlerConfig setStartReferencesFiles(
            List<Path> startReferencesFiles) {
        CollectionUtil.setAll(this.startReferencesFiles, startReferencesFiles);
        return this;
    }

    /**
     * Gets the providers of references used as starting points for crawling.
     * Use this approach when references need to be provided
     * dynamically at launch time.
     * @return start references providers (never <code>null</code>)
     */
    public List<ReferencesProvider> getStartReferencesProviders() {
        return Collections.unmodifiableList(startReferencesProviders);
    }

    /**
     * Sets the providers of references used as starting points for crawling.
     * Use this approach when references need to be provided
     * dynamically at launch time.
     * @param startReferencesProviders start references provider
     * @return this
     */
    public CrawlerConfig setStartReferencesProviders(
            List<ReferencesProvider> startReferencesProviders) {
        CollectionUtil.setAll(
                this.startReferencesProviders, startReferencesProviders);
        CollectionUtil.removeNulls(this.startReferencesProviders);
        return this;
    }

    /**
     * The exceptions we want to stop the crawler on.
     * By default the crawler will log exceptions from processing
     * a document and try to move on to the next without stopping.
     * Even if no exceptions are returned by this method,
     * the crawler can sometimes stop regardless if it cannot recover
     * safely from an exception.
     * To capture more exceptions, use a parent class (e.g., Exception
     * should catch them all).
     * @return exceptions that will stop the crawler when encountered
     */
    public List<Class<? extends Exception>> getStopOnExceptions() {
        return Collections.unmodifiableList(stopOnExceptions);
    }

    /**
     * Sets the exceptions we want to stop the crawler on.
     * By default the crawler will log exceptions from processing
     * a document and try to move on to the next without stopping.
     * Even if no exceptions are returned by this method,
     * the crawler can sometimes stop regardless if it cannot recover
     * safely from an exception.
     * To capture more exceptions, use a parent class (e.g., Exception
     * should catch them all).
     * @param stopOnExceptions exceptions that will stop the crawler when
     *         encountered
     * @return this
     */
    public CrawlerConfig setStopOnExceptions(
            List<Class<? extends Exception>> stopOnExceptions) {
        CollectionUtil.setAll(this.stopOnExceptions, stopOnExceptions);
        return this;
    }

    /**
     * Gets reference filters
     * @return reference filters
     */
    public List<ReferenceFilter> getReferenceFilters() {
        return Collections.unmodifiableList(referenceFilters);
    }

    /**
     * Sets reference filters.
     * @param referenceFilters the referenceFilters to set
     * @return this
     */
    public CrawlerConfig setReferenceFilters(
            List<ReferenceFilter> referenceFilters) {
        CollectionUtil.setAll(this.referenceFilters, referenceFilters);
        return this;
    }

    /**
     * Gets the document filters.
     * @return document filters
     */
    public List<DocumentFilter> getDocumentFilters() {
        return Collections.unmodifiableList(documentFilters);
    }

    /**
     * Sets document filters.
     * @param documentFilters document filters
     * @return this
     */
    public CrawlerConfig setDocumentFilters(
            List<DocumentFilter> documentFilters) {
        CollectionUtil.setAll(this.documentFilters, documentFilters);
        return this;
    }

    /**
     * Gets metadata filters.
     * @return metadata filters
     */
    public List<MetadataFilter> getMetadataFilters() {
        return Collections.unmodifiableList(metadataFilters);
    }

    /**
     * Sets metadata filters.
     * @param metadataFilters metadata filters
     * @return this
     */
    public CrawlerConfig setMetadataFilters(
            List<MetadataFilter> metadataFilters) {
        CollectionUtil.setAll(this.metadataFilters, metadataFilters);
        return this;
    }

    /**
     * Gets Committers responsible for persisting information
     * to a target location/repository.
     * @return list of Committers (never <code>null</code>)
     */
    public List<Committer> getCommitters() {
        return Collections.unmodifiableList(committers);
    }

    /**
     * Sets Committers responsible for persisting information
     * to a target location/repository.
     * @param committers list of Committers
     * @return this
     */
    public CrawlerConfig setCommitters(List<Committer> committers) {
        CollectionUtil.setAll(this.committers, committers);
        return this;
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
     * @return this
     */
    public CrawlerConfig setEventListeners(
            List<EventListener<?>> eventListeners) {
        CollectionUtil.setAll(this.eventListeners, eventListeners);
        return this;
    }

    /**
     * Adds event listeners.
     * Those are considered additions to automatically
     * detected configuration objects implementing {@link EventListener}.
     * @param eventListeners event listeners.
     * @return this
     */
    public CrawlerConfig addEventListeners(
            List<EventListener<?>> eventListeners) {
        this.eventListeners.addAll(eventListeners);
        return this;
    }

    /**
     * Adds an event listener.
     * Those are considered additions to automatically
     * detected configuration objects implementing {@link EventListener}.
     * @param eventListener event listener.
     * @return this
     */
    public CrawlerConfig addEventListener(EventListener<?> eventListener) {
        eventListeners.add(eventListener);
        return this;
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
     * @return this
     */
    public CrawlerConfig clearEventListeners() {
        eventListeners.clear();
        return this;
    }

    /**
     * Gets pre-import consumers.
     * @return pre-import consumers
     */
    public List<DocumentConsumer> getPreImportConsumers() {
        return Collections.unmodifiableList(preImportConsumers);
    }

    /**
     * Sets pre-import consumers.
     * @param preImportConsumers pre-import consumers
     * @return this
     */
    public CrawlerConfig setPreImportConsumers(
            List<DocumentConsumer> preImportConsumers) {
        CollectionUtil.setAll(preImportConsumers, preImportConsumers);
        CollectionUtil.removeNulls(preImportConsumers);
        return this;
    }

    /**
     * Gets post-import consumers.
     * @return post-import consumers
     */
    public List<DocumentConsumer> getPostImportConsumers() {
        return Collections.unmodifiableList(postImportConsumers);
    }

    /**
     * Sets post-import consumers.
     * @param postImportConsumers post-import consumers
     * @return this
     */
    public CrawlerConfig setPostImportConsumers(
            List<DocumentConsumer> postImportConsumers) {
        CollectionUtil.setAll(postImportConsumers, postImportConsumers);
        CollectionUtil.removeNulls(postImportConsumers);
        return this;
    }

    /**
     * One or more fetchers responsible for pulling documents and document
     * metadata associated with a reference from a source.
     * When more than one are configured and for each documents, fetchers will
     * be invoked in their defined order, until the first one that accepts and
     * successfully process a reference (others are not invoked).
     * @return one or more fetchers
     */
    public List<Fetcher<?, ?>> getFetchers() { //NOSONAR
        return Collections.unmodifiableList(fetchers);
    }

    /**
     * One or more fetchers responsible for pulling documents and document
     * metadata associated with a reference from a source.
     * When more than one are configured and for each documents, fetchers will
     * be invoked in their defined order, until the first one that accepts and
     * successfully process a reference (others are not invoked).
     * @param fetchers one or more fetchers
     * @return this
     */
    public CrawlerConfig setFetchers(List<Fetcher<?, ?>> fetchers) {
        CollectionUtil.setAll(this.fetchers, fetchers);
        return this;
    }
}
