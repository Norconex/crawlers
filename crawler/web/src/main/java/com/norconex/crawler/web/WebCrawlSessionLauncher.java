/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.web;

import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.cli.CliLauncher;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerImpl;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.web.doc.HttpDocMetadata;
import com.norconex.crawler.web.doc.HttpDocRecord;
import com.norconex.crawler.web.fetch.HttpFetcherProvider;
import com.norconex.crawler.web.pipeline.committer.HttpCommitterPipeline;
import com.norconex.crawler.web.pipeline.importer.HttpImporterPipeline;
import com.norconex.crawler.web.pipeline.queue.HttpQueuePipeline;
import com.norconex.crawler.web.pipeline.queue.WebQueueInitializer;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebCrawlSessionLauncher {

    //TODO maybe have a WebCrawlerImpl instead, and simply use that one here.
    //TODO maybe rename this class "WebCrawlSession", still having act solely
    // as a launcher.

    /**
     * Invokes the Web Crawler from the command line.
     * You can invoke it without any arguments to get a list of command-line
     * options.
     * @param args command-line options
     */
    public static void main(String[] args) {
        launch(args);
//        new CollectorCommandLauncher().launch(new HttpCollector(), args);
    }


    public static int launch(String... args) {
        return CliLauncher.launch(
            CrawlSession.builder()
                .crawlerFactory(
                    (sess, cfg) -> Crawler.builder()
                        .crawlSession(sess)
                        .crawlerConfig(cfg)
                        .crawlerImpl(crawlerImplBuilder().build())
                        .build()
                )
                .crawlSessionConfig(new CrawlSessionConfig()),
            args
        );
    }

    static CrawlerImpl.CrawlerImplBuilder crawlerImplBuilder() {
        return CrawlerImpl.builder()
            //TODO make fetcher*s* part of crawler-core CONFIG instead?
            .fetcherProvider(new HttpFetcherProvider())
            .beforeCrawlerExecution(
                    WebCrawlSessionLauncher::logCrawlerInformation)
            .queueInitializer(new WebQueueInitializer())
            .queuePipeline(new HttpQueuePipeline())
            .importerPipeline(new HttpImporterPipeline())
            .committerPipeline(new HttpCommitterPipeline())
            .beforeDocumentProcessing(WebCrawlSessionLauncher::initCrawlDoc)
            .beforeDocumentFinalizing(WebCrawlSessionLauncher::preDocFinalizing)

            // Needed??
            .crawlDocRecordType(HttpDocRecord.class)
            .docRecordFactory(ctx -> new HttpDocRecord(
                    ctx.reference()
                    //TODO What about depth, cached doc, etc? It should be
                    // set here unless this is really used just for queue
                    // initialization or set by caller
                    //, 999

                    ))
            ;
    }

    private static void initCrawlDoc(Crawler crawler, CrawlDoc doc) {
        var docRecord = (HttpDocRecord) doc.getDocRecord();
        var cachedDocRecord = (HttpDocRecord) doc.getCachedDocRecord();
        var metadata = doc.getMetadata();

        //TODO consider moving metadata setting elsewhere
        // (and use reflextion?)

        //TODO should DEPTH be set here now that is is in Core?
        metadata.add(HttpDocMetadata.DEPTH, docRecord.getDepth());
        metadata.add(HttpDocMetadata.SM_CHANGE_FREQ,
                docRecord.getSitemapChangeFreq());
        metadata.add(HttpDocMetadata.SM_LASTMOD, docRecord.getSitemapLastMod());
        metadata.add(HttpDocMetadata.SM_PRORITY,
                docRecord.getSitemapPriority());

        // In case the crawl data supplied is from a URL that was pulled
        // from cache because the parent was skipped and could not be
        // extracted normally with link information, we attach referrer
        // data here if null
        // (but only if referrer reference is not null, which should never
        // be in this case as it is set by beforeFinalizeDocumentProcessing()
        // below.
        // We do not need to do this for sitemap information since the queue
        // pipeline takes care of (re)adding it.
        if (cachedDocRecord != null
                && docRecord.getReferrerReference() != null
                && Objects.equals(
                        docRecord.getReferrerReference(),
                        cachedDocRecord.getReferrerReference())
                && (docRecord.getReferrerLinkMetadata() == null)) {
            docRecord.setReferrerLinkMetadata(
                    cachedDocRecord.getReferrerLinkMetadata());
        }

        // Add referrer data to metadata
        //TODO move elsewhere, like .core?
        metadata.add(HttpDocMetadata.REFERRER_REFERENCE,
                docRecord.getReferrerReference());
        if (docRecord.getReferrerLinkMetadata() != null) {
            var linkMeta = new Properties();
            linkMeta.fromString(docRecord.getReferrerLinkMetadata());
            for (Entry<String, List<String>> en : linkMeta.entrySet()) {
                var key = HttpDocMetadata.REFERRER_LINK_PREFIX + en.getKey();
                for (String value : en.getValue()) {
                    if (value != null) {
                        metadata.add(key, value);
                    }
                }
            }
        }

        // Add possible redirect trail
        if (!docRecord.getRedirectTrail().isEmpty()) {
            metadata.setList(HttpDocMetadata.REDIRECT_TRAIL,
                    docRecord.getRedirectTrail());
        }
    }

    private static void preDocFinalizing(Crawler crawler, CrawlDoc doc) {
        // If URLs were not yet extracted, it means no links will be followed.
        // In case the referring document was skipped or has a bad status
        // (which can always be temporary), we should queue for processing any
        // referenced links from cache to make sure an attempt will be made to
        // re-crawl these "child" links and they will not be considered orphans.
        // Else, as orphans they could wrongfully be deleted, ignored, or
        // be re-assigned the wrong depth if linked from another, deeper, page.
        // See: https://github.com/Norconex/collector-http/issues/278


        var httpData = (HttpDocRecord) doc.getDocRecord();
        var httpCachedData = (HttpDocRecord) doc.getCachedDocRecord();

        // If never crawled before, URLs were extracted already, or cached
        // version has no extracted, URLs, abort now.
        if (httpCachedData == null
                || !httpData.getReferencedUrls().isEmpty()
                || httpCachedData.getReferencedUrls().isEmpty()) {
            return;
        }

        // Only continue if the document could not have extracted URLs because
        // it was skipped, or in a temporary invalid state that prevents
        // accessing child links normally.
        var state = httpData.getState();
        if (!state.isSkipped() && !state.isOneOf(
                CrawlDocState.BAD_STATUS, CrawlDocState.ERROR)) {
            return;
        }

        // OK, let's do this
        if (LOG.isDebugEnabled()) {
            LOG.debug("Queueing referenced URLs of {}",
                    httpData.getReference());
        }

        var childDepth = httpData.getDepth() + 1;
        var referencedUrls = httpCachedData.getReferencedUrls();
        for (String url : referencedUrls) {

            var childData = new HttpDocRecord(url, childDepth);
            childData.setReferrerReference(httpData.getReference());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Queueing skipped document's child: {}",
                        childData.getReference());
            }
            crawler.queueDocRecord(childData);
        }
    }

    private static void logCrawlerInformation(Crawler crawler, boolean resume) {
        var cfg = Web.config(crawler);
        LOG.info("RobotsTxt support: {}", !cfg.isIgnoreRobotsTxt());
        LOG.info("RobotsMeta support: {}", !cfg.isIgnoreRobotsMeta());
        LOG.info("Sitemap support: {}", !cfg.isIgnoreSitemap());
        LOG.info("Canonical links support: {}", !cfg.isIgnoreCanonicalLinks());
    }
}
