package com.norconex.crawler.core2.cmd.crawl.pipeline.process;

import static java.util.Optional.ofNullable;

import com.norconex.crawler.core.session.CrawlMode;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.cluster.ClusterContinuousTask;
import com.norconex.crawler.core2.cmd.crawl.pipeline.process.CrawlProcessTask.ProcessQueueAction;
import com.norconex.crawler.core2.doc.CrawlDocContext;
import com.norconex.crawler.core2.doc.CrawlDocMetaConstants;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.ledger.CrawlEntry;
import com.norconex.crawler.core2.ledger.ProcessingOutcome;
import com.norconex.importer.doc.Doc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Continuous worker version of the crawl processing task. Each invocation
 * of executeOne processes at most a single queue entry (or none if queue empty),
 * returning an appropriate WorkResult status. The continuous task framework
 * will repeatedly call this method across nodes.
 */
@Slf4j
@RequiredArgsConstructor
public class CrawlProcessContinuousWorker implements ClusterContinuousTask {

    private final ProcessQueueAction queueAction;

    @Override
    public void onStart(CrawlSession session) {
        ofNullable(
                session.getCrawlContext().getCallbacks().getBeforeCrawlTask())
                        .ifPresent(cb -> cb.accept(session));
    }

    @Override
    public void onStop(CrawlSession session) {
        ofNullable(session.getCrawlContext().getCallbacks().getAfterCrawlTask())
                .ifPresent(cb -> cb.accept(session));
    }

    @Override
    public WorkResult executeOne(CrawlSession session) {
        var crawlCtx = session.getCrawlContext();
        var docProcessCtx = new ProcessContext().crawlSession(session);
        CrawlEntry currentEntry = null;
        try {
            currentEntry =
                    crawlCtx.getCrawlEntryLedger().nextQueued().orElse(null);
            if (currentEntry == null) {
                return WorkResult.noWork();
            }
            var doc = new Doc(currentEntry.getReference());
            CrawlEntry previousEntry = null;
            if (session.getCrawlMode() == CrawlMode.INCREMENTAL) {
                previousEntry = crawlCtx.getCrawlEntryLedger()
                        .getPreviousEntry(currentEntry.getReference())
                        .orElse(null);
            }
            doc.getMetadata().set(CrawlDocMetaConstants.IS_DOC_NEW,
                    previousEntry == null);
            var docContext = CrawlDocContext.builder()
                    .currentCrawlEntry(currentEntry)
                    .previousCrawlEntry(previousEntry)
                    .doc(doc)
                    .build();
            docProcessCtx.docContext(docContext);

            ofNullable(crawlCtx.getCallbacks().getBeforeDocumentProcessing())
                    .ifPresent(bdp -> bdp.accept(session, doc));

            if (queueAction == ProcessQueueAction.DELETE_ALL) {
                ProcessDelete.execute(docProcessCtx);
            } else {
                ProcessUpsert.execute(docProcessCtx);
            }

            ofNullable(crawlCtx.getCallbacks().getAfterDocumentProcessing())
                    .ifPresent(adp -> adp.accept(session,
                            docProcessCtx.docContext().getDoc()));

            return WorkResult.workDone();
        } catch (Exception e) {
            if (handleExceptionAndCheckIfStopCrawler(session, docProcessCtx,
                    e)) {
                // Fatal for this worker only; cluster keeps going
                return WorkResult.fatalFailure();
            }
            return WorkResult.retryableFailure();
        } finally {
            try {
                ProcessFinalize.execute(docProcessCtx);
            } catch (Exception e) { //NOSONAR
                LOG.debug("Finalize error: {}", e.getMessage());
            }
        }
    }

    // true to request crawler stop (maps to fatal failure for this worker)
    private boolean handleExceptionAndCheckIfStopCrawler(
            CrawlSession session,
            ProcessContext docProcessCtx, Exception e) {

        var crawlCtx = session.getCrawlContext();
        var docContext = docProcessCtx.docContext();
        if (docContext == null) {
            LOG.error("Unrecoverable error. Marking fatal.", e);
            crawlCtx.getEventManager().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.CRAWLER_ERROR)
                            .source(session)
                            .exception(e)
                            .build());
            return true;
        }
        docContext.getCurrentCrawlEntry()
                .setProcessingOutcome(ProcessingOutcome.ERROR);
        if (LOG.isDebugEnabled()) {
            LOG.info("Could not process document: {} ({})",
                    docContext.getReference(), e.getMessage(), e);
        } else {
            LOG.info("Could not process document: {} ({})",
                    docContext.getReference(), e.getMessage());
        }
        crawlCtx.getEventManager().fire(
                CrawlerEvent.builder()
                        .name(CrawlerEvent.REJECTED_ERROR)
                        .source(session)
                        .crawlEntry(docContext.getCurrentCrawlEntry())
                        .exception(e)
                        .build());
        try {
            ProcessFinalize.execute(docProcessCtx);
        } catch (Exception ex) {
            /* ignore */ }
        // For first pass we do not enforce stop-on-exception filtering here.
        return false;
    }
}
