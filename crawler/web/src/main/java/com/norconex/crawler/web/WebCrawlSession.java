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
import java.util.Optional;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.cli.CliLauncher;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerImpl;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionBuilder;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.fetch.HttpFetcherProvider;
import com.norconex.crawler.web.pipeline.committer.WebCommitterPipeline;
import com.norconex.crawler.web.pipeline.importer.WebImporterPipeline;
import com.norconex.crawler.web.pipeline.queue.WebQueueInitializer;
import com.norconex.crawler.web.pipeline.queue.WebQueuePipeline;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebCrawlSession {

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
        try {
            System.exit(launch(args));
        } catch (Exception e) {
            e.printStackTrace(System.err); //NOSONAR
            System.exit(1);
        }
    }

    public static int launch(String... args) {
        return CliLauncher.launch(
                initCrawlSessionBuilder(
                        CrawlSession.builder(),
                        new CrawlSessionConfig(WebCrawlerConfig.class)),
                args);
    }

    public static CrawlSession createSession(CrawlSessionConfig sessionConfig) {
        return initCrawlSessionBuilder(
                CrawlSession.builder(),
                Optional.ofNullable(sessionConfig).orElseGet(() ->
                        new CrawlSessionConfig(WebCrawlerConfig.class)))
                .build();
    }

    // Return same builder, for chaining
    static CrawlSessionBuilder initCrawlSessionBuilder(
            CrawlSessionBuilder builder, CrawlSessionConfig sessionConfig) {
        builder.crawlerFactory(
                (sess, cfg) -> Crawler.builder()
                    .crawlSession(sess)
                    .crawlerConfig(cfg)
                    .crawlerImpl(crawlerImplBuilder().build())
                    .build()
            )
            .crawlSessionConfig(sessionConfig);
        return builder;
    }


    static CrawlerImpl.CrawlerImplBuilder crawlerImplBuilder() {
        return CrawlerImpl.builder()
            //TODO make fetcher*s* part of crawler-core CONFIG instead?
            .fetcherProvider(new HttpFetcherProvider())
            .beforeCrawlerExecution(
                    WebCrawlSession::logCrawlerInformation)
            .queueInitializer(new WebQueueInitializer())
            .queuePipeline(new WebQueuePipeline())
            .importerPipeline(new WebImporterPipeline())
            .committerPipeline(new WebCommitterPipeline())
            .beforeDocumentProcessing(WebCrawlSession::initCrawlDoc)
            .beforeDocumentFinalizing(WebCrawlSession::preDocFinalizing)

            // Needed??
            .crawlDocRecordType(WebDocRecord.class)
            .docRecordFactory(ctx -> new WebDocRecord(
                    ctx.reference()
                    //TODO What about depth, cached doc, etc? It should be
                    // set here unless this is really used just for queue
                    // initialization or set by caller
                    //, 999

                    ))
            ;
    }

    private static void initCrawlDoc(Crawler crawler, CrawlDoc doc) {
        var docRecord = (WebDocRecord) doc.getDocRecord();
        var cachedDocRecord = (WebDocRecord) doc.getCachedDocRecord();
        var metadata = doc.getMetadata();

        //TODO consider moving metadata setting elsewhere
        // (and use reflextion?)

        //TODO should DEPTH be set here now that is is in Core?
        metadata.add(WebDocMetadata.DEPTH, docRecord.getDepth());
        metadata.add(WebDocMetadata.SM_CHANGE_FREQ,
                docRecord.getSitemapChangeFreq());
        metadata.add(WebDocMetadata.SM_LASTMOD, docRecord.getSitemapLastMod());
        metadata.add(WebDocMetadata.SM_PRORITY,
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
        metadata.add(WebDocMetadata.REFERRER_REFERENCE,
                docRecord.getReferrerReference());
        if (docRecord.getReferrerLinkMetadata() != null) {
            var linkMeta = new Properties();
            linkMeta.fromString(docRecord.getReferrerLinkMetadata());
            for (Entry<String, List<String>> en : linkMeta.entrySet()) {
                var key = WebDocMetadata.REFERRER_LINK_PREFIX + en.getKey();
                for (String value : en.getValue()) {
                    if (value != null) {
                        metadata.add(key, value);
                    }
                }
            }
        }

        // Add possible redirect trail
        if (!docRecord.getRedirectTrail().isEmpty()) {
            metadata.setList(WebDocMetadata.REDIRECT_TRAIL,
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


        var httpData = (WebDocRecord) doc.getDocRecord();
        var httpCachedData = (WebDocRecord) doc.getCachedDocRecord();

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

            var childData = new WebDocRecord(url, childDepth);
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
        LOG.info("""
            Enabled features:

            RobotsTxt:        %s
            RobotsMeta:       %s
            Sitemap:          %s
            Canonical links:  %s
            Metadata:
              Checksummer:    %s
              Deduplication:  %s
            Document:
              Checksummer:    %s
              Deduplication:  %s
            """.formatted(
                    yn(!cfg.isIgnoreRobotsTxt()),
                    yn(!cfg.isIgnoreRobotsMeta()),
                    yn(!cfg.isIgnoreSitemap()),
                    yn(!cfg.isIgnoreCanonicalLinks()),
                    yn(cfg.getMetadataChecksummer() != null),
                    yn(cfg.isMetadataDeduplicate()
                            && cfg.getMetadataChecksummer() != null),
                    yn(cfg.getDocumentChecksummer() != null),
                    yn(cfg.isDocumentDeduplicate()
                            && cfg.getDocumentChecksummer() != null)
            ));
    }
    private static String yn(boolean value) {
        return value ? "Yes" : "No";
    }
}
