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
package com.norconex.crawler.core.crawler;

import static com.norconex.crawler.core.crawler.CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN;
import static com.norconex.crawler.core.crawler.CrawlerEvent.CRAWLER_RUN_THREAD_END;

import java.util.concurrent.CountDownLatch;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.time.Timer;
import com.norconex.crawler.core.crawler.CrawlerImpl.DocRecordFactoryContext;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.crawler.core.doc.CrawlState;
import com.norconex.crawler.core.monitor.MdcUtil;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategy;
import com.norconex.crawler.core.spoil.impl.GenericSpoiledReferenceStrategizer;
import com.norconex.importer.response.ImporterResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * A crawler thread that reads and processes the reference queue.
 */
@Slf4j
//TODO maybe: rename CrawlerThread?
class ReferencesProcessor implements Runnable {

    // Needed?
    private static final int MINIMUM_DELAY = 1;

    private final Crawler.ProcessFlags flags;
    private final CountDownLatch latch;
    private final int threadIndex;
    private final Crawler crawler;

    //TODO maybe: put the constructor args into a ReferenceProcessorContext
    // and merge ProcessFlags with that class.
    ReferencesProcessor(
            Crawler crawler,
            CountDownLatch latch,
            Crawler.ProcessFlags flags,
            int threadIndex) {
        this.crawler = crawler;
        this.latch = latch;
        this.flags = flags;
        this.threadIndex = threadIndex;
    }

    @Override
    public void run() {
        MdcUtil.setCrawlerId(crawler.getId());
        Thread.currentThread().setName(crawler.getId() + "#" + threadIndex);

        LOG.debug("Crawler thread #{} started.", threadIndex);

        try {
            crawler.fire(CRAWLER_RUN_THREAD_BEGIN, Thread.currentThread());
            while (!crawler.isStopped()) {
                try {
                    var status =
                            processNextReference(flags);
                    if (status == ReferenceProcessStatus.MAX_REACHED) {
                        crawler.stop();
                        break;
                    }
                    if (status == ReferenceProcessStatus.QUEUE_EMPTY) {
                        if (crawler.getQueueInitialized()) {
                            break;
                        }
                        LOG.info("References are still being queued. "
                                + "Waiting for new references...");
                        Sleeper.sleepSeconds(5);
                    }
                } catch (Exception e) {
                    LOG.error("""
                          An error occured that could compromise\s\
                          the stability of the crawler. Stopping\s\
                          execution to avoid further issues...""", e);
                    crawler.stop();
                }
            }
        } catch (Exception e) {
            LOG.error("Problem in thread execution.", e);
        } finally {
            latch.countDown();
            crawler.fire(CRAWLER_RUN_THREAD_END, Thread.currentThread());
        }
    }

    private enum ReferenceProcessStatus {
        MAX_REACHED,
        QUEUE_EMPTY,
        OK;
    }

    // return <code>OK</code> if more references to process
    private ReferenceProcessStatus processNextReference(
            final Crawler.ProcessFlags flags) {

        if (!flags.isDelete() && isMaxDocuments()) {
            LOG.info("Maximum documents reached: {}",
                    crawler.getCrawlerConfig().getMaxDocuments());
            return ReferenceProcessStatus.MAX_REACHED;
        }
        var queuedDocInfo = crawler.getDocInfoService().pollQueue();

        LOG.trace("Processing next reference from Queue: {}",
                queuedDocInfo);
        if (queuedDocInfo.isPresent()) {
            var elapsedTime = Timer.timeWatch(() ->
                    processNextQueuedCrawlData(queuedDocInfo.get(), flags))
                            .toString();
            LOG.debug("{} to process: {}", elapsedTime,
                    queuedDocInfo.get().getReference());
        } else {
            var activeCount = crawler.getDocInfoService().getActiveCount();
            var queueEmpty = crawler.getDocInfoService().isQueueEmpty();
            LOG.trace("Number of references currently being processed: {}",
                    activeCount);
            LOG.trace("Is reference queue empty? {}", queueEmpty);
            if (activeCount == 0 && queueEmpty) {
                return ReferenceProcessStatus.QUEUE_EMPTY;
            }
            Sleeper.sleepMillis(MINIMUM_DELAY);
        }
        return ReferenceProcessStatus.OK;
    }

    private void processNextQueuedCrawlData(
            CrawlDocRecord docInfo, Crawler.ProcessFlags flags) {
        var reference = docInfo.getReference();

        var cachedDocInfo =
                crawler.getDocInfoService().getCached(reference).orElse(null);

        var doc = new CrawlDoc(
                docInfo,
                cachedDocInfo,
                crawler.getStreamFactory().newInputStream(),
                flags.isOrphan());

        var context = new ImporterPipelineContext(crawler, doc);

        doc.getMetadata().set(
                CrawlDocMetadata.IS_CRAWL_NEW,
                cachedDocInfo == null);

// Needed? Move to CrawlerImpl?
//        initCrawlDoc(doc);

        try {
            if (flags.isDelete()) {
                deleteReference(doc);
                finalizeDocumentProcessing(doc);
                return;
            }
            LOG.debug("Processing reference: {}", reference);

            var response = crawler.getCrawlerImpl()
                    .importerPipelineExecutor.apply(context);

            if (response != null) {
                processImportResponse(response, doc);//docInfo, cachedDocInfo);
            } else {
                if (docInfo.getState().isNewOrModified()) {
                    docInfo.setState(CrawlState.REJECTED);
                }
                //TODO Fire an event here? If we get here, the importer did
                //not kick in,
                //so do not fire REJECTED_IMPORT (like it used to).
                //Errors should have fired
                //something already so do not fire two REJECTED... but
                //what if a previous issue did not fire a REJECTED_*?
                //This should not happen, but keep an eye on that.
                //OR do we want to always fire REJECTED_IMPORT on import failure
                //(in addition to whatever) and maybe a new REJECTED_COLLECTOR
                //when it did not reach the importer module?
                finalizeDocumentProcessing(doc);
            }
        } catch (Throwable e) {
            //TODO do we really want to catch anything other than
            // HTTPFetchException?  In case we want special treatment to the
            // class?
            docInfo.setState(CrawlState.ERROR);
            crawler.getEventManager().fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_ERROR)
                    .source(crawler)
                    .crawlDocRecord(docInfo)
                    .exception(e)
                    .build());
            if (LOG.isDebugEnabled()) {
                LOG.info("Could not process document: {} ({})",
                        reference, e.getMessage(), e);
            } else {
                LOG.info("Could not process document: {} ({})",
                        reference, e.getMessage());
            }
            finalizeDocumentProcessing(doc);

            // Rethrow exception is we want the crawler to stop
            var exceptionClasses =
                    crawler.getCrawlerConfig().getStopOnExceptions();
            if (CollectionUtils.isNotEmpty(exceptionClasses)) {
                for (Class<? extends Exception> c : exceptionClasses) {
                    if (c.isAssignableFrom(e.getClass())) {
                        throw e;
                    }
                }
            }
        }
    }



    private boolean isMaxDocuments() {
        var maxDocs = crawler.getCrawlerConfig().getMaxDocuments();
        //TODO replace check for "processedCount" vs "maxDocuments"
        // with event counts vs max committed, max processed, max etc...
        return maxDocs > -1
                && crawler.getMonitor().getProcessedCount() >= maxDocs;
    }

    private void processImportResponse(
            ImporterResponse response, CrawlDoc doc) {

        var docInfo = doc.getDocInfo();

        var msg = response.getImporterStatus().toString();
        if (response.getNestedResponses().length > 0) {
            msg += "(" + response.getNestedResponses().length
                    + " nested responses.)";
        }

        if (response.isSuccess()) {
            crawler.getEventManager().fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.DOCUMENT_IMPORTED)
                    .source(crawler)
                    .crawlDocRecord(docInfo)
                    .subject(response)
                    .message(msg)
                    .build());
            crawler.getCrawlerImpl().committerPipelineExecutor.accept(
                    crawler, doc);
        } else {
            docInfo.setState(CrawlState.REJECTED);
            crawler.getEventManager().fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_IMPORT)
                    .source(crawler)
                    .crawlDocRecord(docInfo)
                    .subject(response)
                    .message(msg)
                    .build());
            LOG.debug("Importing unsuccessful for \"{}\": {}",
                    docInfo.getReference(),
                    response.getImporterStatus().getDescription());
        }
        finalizeDocumentProcessing(doc);
        var children = response.getNestedResponses();
        for (ImporterResponse childResponse : children) {
            //TODO have a createEmbeddedDoc method instead?
                // TODO have a docInfoFactory instead and arguments
                 /// dictate whether it is a child, embedded, or top level
            var childDocRec = crawler.getCrawlerImpl().docRecordFactory.apply(
                    DocRecordFactoryContext.builder()
                        .reference(childResponse.getReference())
                        .parentDocRecord(docInfo)
                        .build());
//            CrawlDocRecord childDocInfo = createChildDocInfo(
//                    childResponse.getReference(), docInfo);
            var childCachedDocInfo =
                    crawler.getDocInfoService().getCached(
                            childResponse.getReference()).orElse(null);

            // Here we create a CrawlDoc since the document from the response
            // is (or can be) just a Doc, which does not hold all required
            // properties for crawling.
            //TODO refactor Doc vs CrawlDoc to have only one instance
            // so we do not have to create such copy?
            var childResponseDoc = childResponse.getDocument();
            var childCrawlDoc = new CrawlDoc(
                    childDocRec, childCachedDocInfo,
                    childResponseDoc == null
                            ? CachedInputStream.cache(new NullInputStream(0))
                            : childResponseDoc.getInputStream());
            if (childResponseDoc != null) {
                childCrawlDoc.getMetadata().putAll(
                        childResponseDoc.getMetadata());
            }

            processImportResponse(childResponse, childCrawlDoc);
        }
    }

    private void finalizeDocumentProcessing(CrawlDoc doc) {

        var docInfo = doc.getDocInfo();
        var cachedDocInfo = doc.getCachedDocInfo();

        //--- Ensure we have a state -------------------------------------------
        if (docInfo.getState() == null) {
            LOG.warn("Reference status is unknown for \"{}\". "
                    + "This should not happen. Assuming bad status.",
                    docInfo.getReference());
            docInfo.setState(CrawlState.BAD_STATUS);
        }

        try {

            // important to call this before copying properties further down
            crawler.getCrawlerImpl().beforeDocumentFinalizing().accept(crawler, doc);

            //--- If doc crawl was incomplete, set missing info from cache -----
            // If document is not new or modified, it did not go through
            // the entire crawl life cycle for a document so maybe not all info
            // could be gathered for a reference.  Since we do not want to lose
            // previous information when the crawl was effective/good
            // we copy it all that is non-null from cache.
            if (!docInfo.getState().isNewOrModified() && cachedDocInfo != null) {
                //TODO maybe new CrawlData instances should be initialized with
                // some of cache data available instead?
                BeanUtil.copyPropertiesOverNulls(docInfo, cachedDocInfo);
            }

            //--- Deal with bad states (if not already deleted) ----------------
            if (!docInfo.getState().isGoodState()
                    && !docInfo.getState().isOneOf(CrawlState.DELETED)) {

                //TODO If duplicate, consider it as spoiled if a cache version
                // exists in a good state.
                // This involves elaborating the concept of duplicate
                // or "reference change" in this core project. Otherwise there
                // is the slim possibility right now that a Collector
                // implementation marking references as duplicate may
                // generate orphans (which may be caught later based
                // on how orphans are handled, but they should not be ever
                // considered orphans in the first place).
                // This could remove the need for the
                // markReferenceVariationsAsProcessed(...) method

                var strategy =
                        getSpoiledStateStrategy(docInfo);

                if (strategy == SpoiledReferenceStrategy.IGNORE) {
                    LOG.debug("Ignoring spoiled reference: {}",
                            docInfo.getReference());
                } else if (strategy == SpoiledReferenceStrategy.DELETE) {
                    // Delete if previous state exists and is not already
                    // marked as deleted.
                    if (cachedDocInfo != null
                            && !cachedDocInfo.getState().isOneOf(
                                    CrawlState.DELETED)) {
                        deleteReference(doc);
                    }
                } else // GRACE_ONCE:
                // Delete if previous state exists and is a bad state,
                // but not already marked as deleted.
                if (cachedDocInfo != null
                        && !cachedDocInfo.getState().isOneOf(
                                CrawlState.DELETED)) {
                    if (!cachedDocInfo.getState().isGoodState()) {
                        deleteReference(doc);
                    } else {
                        LOG.debug("""
                        	This spoiled reference is\s\
                        	being graced once (will be deleted\s\
                        	next time if still spoiled): {}""",
                                docInfo.getReference());
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Could not finalize processing of: {} ({})",
                    docInfo.getReference(), e.getMessage(), e);
        }

        //--- Mark reference as Processed --------------------------------------
        try {
            crawler.getDocInfoService().processed(docInfo);
            markReferenceVariationsAsProcessed(docInfo);

            crawler.getProgressLogger().logProgress();


        } catch (Exception e) {
            LOG.error("Could not mark reference as processed: {} ({})",
                    docInfo.getReference(), e.getMessage(), e);
        }

        try {
            doc.getInputStream().dispose();
        } catch (Exception e) {
            LOG.error("Could not dispose of resources.", e);
        }
    }

    protected void markReferenceVariationsAsProcessed(CrawlDocRecord docInfo) {
        // Mark original URL as processed
        var originalRef = docInfo.getOriginalReference();
        var finalRef = docInfo.getReference();
        if (StringUtils.isNotBlank(originalRef)
                && ObjectUtils.notEqual(originalRef, finalRef)) {

            var originalDocInfo = docInfo.withReference(originalRef);
            originalDocInfo.setOriginalReference(null);
            crawler.getDocInfoService().processed(originalDocInfo);
        }
    }

    private SpoiledReferenceStrategy getSpoiledStateStrategy(
            CrawlDocRecord crawlData) {
        var strategyResolver =
                crawler.getCrawlerConfig().getSpoiledReferenceStrategizer();
        var strategy =
                strategyResolver.resolveSpoiledReferenceStrategy(
                        crawlData.getReference(), crawlData.getState());
        if (strategy == null) {
            // Assume the generic default (DELETE)
            strategy =  GenericSpoiledReferenceStrategizer
                    .DEFAULT_FALLBACK_STRATEGY;
        }
        return strategy;
    }

    private void deleteReference(CrawlDoc doc) {
        LOG.debug("Deleting reference: {}", doc.getReference());

        doc.getDocInfo().setState(CrawlState.DELETED);

        // Event triggered by service
        crawler.getCommitterService().delete(doc);
    }
}
