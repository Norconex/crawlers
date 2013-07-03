/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.crawler;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.db.ICrawlURLDatabase;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.handler.IDelayResolver;
import com.norconex.collector.http.handler.IHttpDocumentChecksummer;
import com.norconex.collector.http.handler.IHttpDocumentProcessor;
import com.norconex.collector.http.handler.IHttpHeadersChecksummer;
import com.norconex.collector.http.handler.IHttpHeadersFetcher;
import com.norconex.collector.http.handler.IURLExtractor;
import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.committer.ICommitter;
import com.norconex.commons.lang.io.FileUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.Importer;
import com.norconex.importer.filter.IOnMatchFilter;
import com.norconex.importer.filter.OnMatch;

/**
 * Holds the URL processing logic in various processing "step" for better
 * readability and maintainability.  Instances are only valid for 
 * the scope of processing a URL.  
 * @author Pascal Essiembre
 */
public class URLProcessor {

    private static final Logger LOG = LogManager.getLogger(URLProcessor.class);
    
    private final HttpCrawler crawler;
    private final HttpCrawlerConfig config;
    private final List<IHttpCrawlerEventListener> listeners = 
            new ArrayList<IHttpCrawlerEventListener>();
    private final CrawlURL crawlURL;
    private final String url;
    private final DefaultHttpClient httpClient;
    private final HttpDocument doc;
    private final IHttpHeadersFetcher hdFetcher;
    private final ICrawlURLDatabase database;
    private final File outputFile;
    private RobotsTxt robotsTxt;
    private RobotsMeta robotsMeta;
    
    // Order is important.  E.g. Robots must be after URL Filters and before 
    // Delay resolver
    private final IURLProcessingStep[] steps = new IURLProcessingStep[] {
        new DepthValidationStep(),
        new URLFiltersStep(),
        new RobotsTxtFiltersStep(),
        new DelayResolverStep(),
        new HttpHeadersFetcherHEADStep(),
        new HttpHeadersFiltersHEADStep(),
        new HttpHeadersChecksumHEADStep(),
        new DocumentFetcherStep(),
        new RobotsMetaCreateStep(),
        new URLExtractorStep(),
        new StoreNextURLsStep(),
        new RobotsMetaNoIndexStep(),
        new HttpHeadersFilterGETStep(),
        new HttpHeadersChecksumGETStep(),
        new DocumentFiltersStep(),
        new DocumentPreProcessingStep(),
        new ImportModuleStep(),
        new HTTPDocumentChecksumStep(),
        new DocumentPostProcessingStep(),
        new DocumentCommitStep()
    };

    public URLProcessor(
            HttpCrawler crawler, DefaultHttpClient httpClient, 
            ICrawlURLDatabase database, File outputFile,
            HttpDocument doc, CrawlURL crawlURL) {
        this.crawler = crawler;
        this.httpClient = httpClient;
        this.database = database;
        this.doc = doc;
        this.crawlURL = crawlURL;
        this.url = crawlURL.getUrl();
        this.config = crawler.getCrawlerConfig();
        this.hdFetcher = config.getHttpHeadersFetcher();
        this.outputFile = outputFile; 
        
        IHttpCrawlerEventListener[] ls = config.getCrawlerListeners();
        if (ls != null) {
            this.listeners.addAll(Arrays.asList(ls));
        }
    }

    public boolean processURL() {
        for (int i = 0; i < steps.length; i++) {
            IURLProcessingStep step = steps[i];
            if (!step.processURL()) {
                return false;
            }
        }
        return true;
    }

    public interface IURLProcessingStep {
        // Returns true to continue to next step
        // Returns false to abort, this URL is rejected.
        boolean processURL();
    }


    //--- URL Depth ------------------------------------------------------------
    private class DepthValidationStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (config.getMaxDepth() != -1 
                    && crawlURL.getDepth() > config.getMaxDepth()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("URL too deep to process (" 
                            + crawlURL.getDepth() + "): " + url);
                }
                crawlURL.setStatus(CrawlStatus.TOO_DEEP);
                return false;
            }
            return true;
        }
    }
    
    //--- URL Filters ----------------------------------------------------------
    private class URLFiltersStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (isURLRejected(config.getURLFilters(), null)) {
                crawlURL.setStatus(CrawlStatus.REJECTED);
                return false;
            }
            return true;
        }
    }
    
    //--- Robots.txt Filters ---------------------------------------------------
    private class RobotsTxtFiltersStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (!config.isIgnoreRobotsTxt()) {
                robotsTxt = config.getRobotsTxtProvider().getRobotsTxt(
                                httpClient, url);
                if (isURLRejected(robotsTxt.getFilters(), robotsTxt)) {
                    crawlURL.setStatus(CrawlStatus.REJECTED);
                    return false;
                }
            }
            return true;
        }
    }
    
    //--- Wait for delay to expire ---------------------------------------------
    private class DelayResolverStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            IDelayResolver delayResolver = config.getDelayResolver();
            if (delayResolver != null) {
                synchronized (delayResolver) {
                    delayResolver.delay(robotsTxt, url);
                }
            }
            return true;
        }
    }
    
    //--- HTTP Headers Fetcher -------------------------------------------------
    private class HttpHeadersFetcherHEADStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (hdFetcher != null) {
                Properties metadata = 
                        hdFetcher.fetchHTTPHeaders(httpClient, url);
                if (metadata == null) {
                    crawlURL.setStatus(CrawlStatus.REJECTED);
                    return false;
                }
                doc.getMetadata().putAll(metadata);
                enhanceHTTPHeaders(doc.getMetadata());
                for (IHttpCrawlerEventListener listener : listeners) {
                    listener.documentHeadersFetched(
                            crawler, url, hdFetcher, doc.getMetadata());
                }
            }
            return true;
        }
    }
    
    //--- HTTP Headers Filters -------------------------------------------------
    private class HttpHeadersFiltersHEADStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (hdFetcher != null && isHeadersRejected()) {
                crawlURL.setStatus(CrawlStatus.REJECTED);
                return false;
            }
            return true;
        }
    }

    //--- HTTP Headers Checksum ------------------------------------------------
    private class HttpHeadersChecksumHEADStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            //TODO only if an INCREMENTAL run... else skip.
            if (hdFetcher != null && isHeadersChecksumRejected()) {
                crawlURL.setStatus(CrawlStatus.UNMODIFIED);
                return false;
            }
            return true;
        }
    }
    
    //--- Document Fetcher -----------------------------------------------------            
    private class DocumentFetcherStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            //TODO for now we assume the document is downloadable.
            // download as file
            CrawlStatus status = config.getHttpDocumentFetcher().fetchDocument(
                    httpClient, doc);
            if (status == CrawlStatus.OK) {
                for (IHttpCrawlerEventListener listener : listeners) {
                    listener.documentFetched(
                            crawler, doc, config.getHttpDocumentFetcher());
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug("SAVED DOC: " 
                          + doc.getLocalFile().toURI() + " [mime type: "
                          + doc.getMetadata().getContentType() + "]");
                }
            }
            crawlURL.setStatus(status);
            if (crawlURL.getStatus() != CrawlStatus.OK) {
                return false;
            }
            return true;
        }
    }
    
    //--- Robots Meta Creation -------------------------------------------------
    private class RobotsMetaCreateStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (!config.isIgnoreRobotsMeta()) {
                try {
                    FileReader reader = new FileReader(doc.getLocalFile());
                    robotsMeta = config.getRobotsMetaProvider().getRobotsMeta(
                            reader, url,  doc.getMetadata().getContentType(),
                            doc.getMetadata());
                    reader.close();
                } catch (IOException e) {
                    throw new HttpCollectorException(
                            "Cannot create RobotsMeta for URL: " + url, e);
                }
            }
            return true;
        }
    }

    //--- Robots Meta NoIndex Check --------------------------------------------
    private class RobotsMetaNoIndexStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            boolean canIndex = config.isIgnoreRobotsMeta() || robotsMeta == null
                    || !robotsMeta.isNoindex();
            if (!canIndex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Document skipped due to Robots meta noindex "
                          + "rule: " + url);
                }
                crawlURL.setStatus(CrawlStatus.REJECTED);
                return false;
            }
            return canIndex;
        }
    }

    
    //--- URL Extractor --------------------------------------------------------
    /*
     * Extract URLs before sending to importer (because the importer may
     * strip some "valid" urls in producing content-centric material.
     * Plus, any additional urls could be added to Metadata and they will
     * be considered.
     */
    private class URLExtractorStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (robotsMeta != null && robotsMeta.isNofollow()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("No URLs extracted due to Robots nofollow rule "
                            + "for URL: " + url);
                }
                return true;
            }
            
            Set<String> urls = null;
            try {
                FileReader reader = new FileReader(doc.getLocalFile());
                IURLExtractor urlExtractor = config.getUrlExtractor();
                urls = urlExtractor.extractURLs(reader, doc.getUrl(), 
                        doc.getMetadata().getContentType());
                reader.close();
            } catch (IOException e) {
                throw new HttpCollectorException(
                        "Cannot extract URLs from: " + url, e);
            }
            
            // Normalize urls
            if (config.getUrlNormalizer() != null) {
                Set<String> nurls = new HashSet<String>();
                for (String extractedURL : urls) {
                    String n = config.getUrlNormalizer().normalizeURL(
                            extractedURL);
                    if (n != null) {
                        nurls.add(n);
                    }
                }
                urls = nurls;
            }
            
            if (urls != null) {
                doc.getMetadata().addString(HttpMetadata.REFERNCED_URLS, 
                        urls.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
            }
            for (IHttpCrawlerEventListener listener : listeners) {
                listener.documentURLsExtracted(crawler, doc);
            }

            return true;
        }
    }

    //--- Store Next URLs to process -------------------------------------------
    private class StoreNextURLsStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (robotsMeta != null && robotsMeta.isNofollow()) {
                return true;
            }
            
            Collection<String> urls = doc.getMetadata().getDocumentUrls();
            for (String urlToProcess : urls) {
                if (database.isActive(urlToProcess)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Already being processed: " + urlToProcess);
                    }
                } else if (database.isQueued(urlToProcess)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Already queued: " + urlToProcess);
                    }
                } else if (database.isProcessed(urlToProcess)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Already processed: " + urlToProcess);
                    }
                } else {
                    database.queue(urlToProcess, crawlURL.getDepth() + 1);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Queued for processing: " + urlToProcess);
                    }
                }
            }
            return true;
        }
    }
    
    
    //--- Headers filters if not done already ----------------------------------
    private class HttpHeadersFilterGETStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (hdFetcher == null) {
                enhanceHTTPHeaders(doc.getMetadata());
                if (isHeadersRejected()) {
                    crawlURL.setStatus(CrawlStatus.REJECTED);
                    return false;
                }
            }
            return true;
        }
    }    
    
    
    //--- HTTP Headers Checksum if not done already ----------------------------
    private class HttpHeadersChecksumGETStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            //TODO only if an INCREMENTAL run... else skip.
            if (hdFetcher == null && isHeadersChecksumRejected()) {
                crawlURL.setStatus(CrawlStatus.UNMODIFIED);
                return false;
            }
            return true;
        }
    }    
    

    
    //--- Document Filters -----------------------------------------------------
    private class DocumentFiltersStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            IHttpDocumentFilter[] filters = config.getHttpDocumentfilters();
            if (filters == null) {
                return true;
            }
            
            boolean hasIncludes = false;
            boolean atLeastOneIncludeMatch = false;
            for (IHttpDocumentFilter filter : filters) {
                boolean accepted = filter.acceptDocument(doc);
                boolean isInclude = filter instanceof IOnMatchFilter
                       && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();

                // Deal with includes
                if (isInclude) {
                    hasIncludes = true;
                    if (accepted) {
                        atLeastOneIncludeMatch = true;
                    }
                    continue;
                }
                
                // Deal with exclude and non-OnMatch filters
                if (accepted) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(String.format(
                                "ACCEPTED document. URL=%s Filter=%s",
                                doc.getUrl(), filter));
                    }
                } else {
                    for (IHttpCrawlerEventListener listener : listeners) {
                        listener.documentRejected(crawler, doc, filter);
                    }
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(String.format(
                                "REJECTED document. URL=%s Filter=%s",
                                doc.getUrl(), filter));
                    }
                    crawlURL.setStatus(CrawlStatus.REJECTED);
                    return false;
                }
            }
            if (hasIncludes && !atLeastOneIncludeMatch) {
                for (IHttpCrawlerEventListener listener : listeners) {
                    listener.documentRejected(crawler, doc, null);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "REJECTED document. URL=%s "
                          + "Filter=(no include filters matched)",
                            doc.getUrl(), null));
                }
                crawlURL.setStatus(CrawlStatus.REJECTED);
                return false;
            }
            return true;
        }
    }    

    //--- Document Pre-Processing ----------------------------------------------
    private class DocumentPreProcessingStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (config.getPreImportProcessors() != null) {
                for (IHttpDocumentProcessor preProc :
                        config.getPreImportProcessors()) {
                    preProc.processDocument(httpClient, doc);
                    for (IHttpCrawlerEventListener listener : listeners) {
                        listener.documentPreProcessed(crawler, doc, preProc);
                    }
                }
            }
            return true;
        }
    }    

    //--- IMPORT Module --------------------------------------------------------
    private class ImportModuleStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            Importer importer = new Importer(config.getImporterConfig());
            try {
                FileUtil.createDirsForFile(outputFile);
                if (importer.importDocument(
                        doc.getLocalFile(),
                        doc.getMetadata().getContentType(),
                        outputFile,
                        doc.getMetadata(),
                        doc.getUrl())) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("ACCEPTED document import. URL="
                                + doc.getUrl());
                    }
                    for (IHttpCrawlerEventListener listener : listeners) {
                        listener.documentImported(crawler, doc);
                    }
                    return true;
                }
            } catch (IOException e) {
                throw new HttpCollectorException(
                        "Cannot import URL: " + url, e);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED document import.  URL=" + doc.getUrl());
            }
            crawlURL.setStatus(CrawlStatus.REJECTED);
            return false;
        }
    }    

    
    //--- HTTP Document Checksum -----------------------------------------------
    private class HTTPDocumentChecksumStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            //TODO only if an INCREMENTAL run... else skip.
            IHttpDocumentChecksummer check = 
                    config.getHttpDocumentChecksummer();
            if (check == null) {
                return true;
            }
            String newDocChecksum = check.createChecksum(doc);
            crawlURL.setDocChecksum(newDocChecksum);
            String oldDocChecksum = null;
            CrawlURL cachedURL = database.getCached(crawlURL.getUrl());
            if (cachedURL != null) {
                oldDocChecksum = cachedURL.getDocChecksum();
            } else {
                LOG.debug("ACCEPTED document checkum (new): URL=" 
                        + crawlURL.getUrl());
                return true;
            }
            if (StringUtils.isNotBlank(newDocChecksum) 
                    && ObjectUtils.equals(newDocChecksum, oldDocChecksum)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("REJECTED document checkum (unmodified): URL=" 
                            + crawlURL.getUrl());
                }
                crawlURL.setStatus(CrawlStatus.UNMODIFIED);
                return false;
            }
            LOG.debug("ACCEPTED document checkum (modified): URL=" 
                    + crawlURL.getUrl());
            return true;
        }
    }   
    
    
    //--- Document Post-Processing ---------------------------------------------
    private class DocumentPostProcessingStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (config.getPostImportProcessors() != null) {
                for (IHttpDocumentProcessor postProc :
                        config.getPostImportProcessors()) {
                    postProc.processDocument(httpClient, doc);
                    for (IHttpCrawlerEventListener listener : listeners) {
                        listener.documentPostProcessed(crawler, doc, postProc);
                    }
                }            
            }
            return true;
        }
    }  
    
    //--- Document Commit ------------------------------------------------------
    private class DocumentCommitStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            ICommitter committer = config.getCommitter();
            if (committer != null) {
                committer.queueAdd(url, outputFile, doc.getMetadata());
            }
            for (IHttpCrawlerEventListener listener : listeners) {
                listener.documentCrawled(crawler, doc);
            }
            return true;
        }
    }  

    //=== Utility methods ======================================================
    private boolean isURLRejected(IURLFilter[] filters, RobotsTxt robots) {
        if (filters == null) {
            return false;
        }
        String type = "";
        if (robots != null) {
            type = " (robots.txt)";
        }
        boolean hasIncludes = false;
        boolean atLeastOneIncludeMatch = false;
        for (IURLFilter filter : filters) {
            boolean accepted = filter.acceptURL(url);
            boolean isInclude = filter instanceof IOnMatchFilter
                   && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
            
            // Deal with includes
            if (isInclude) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }

            // Deal with exclude and non-OnMatch filters
            if (accepted) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ACCEPTED document URL" + type + ". URL=" + url
                            + " Filter=" + filter);
                }
            } else {
                fireDocumentRejected(filter, robots, type);
                return true;
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            fireDocumentRejected(null, null, " (no include filters matched)");
            return true;
        }
        return false;
    }
    
    private void fireDocumentRejected(
            IURLFilter filter, RobotsTxt robots, String type) {
        for (IHttpCrawlerEventListener listener : listeners) {
            if (robots != null) {
                listener.documentRobotsTxtRejected(
                        crawler, url, filter, robots);
            } else {
                listener.documentURLRejected(crawler, url, filter);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("REJECTED document URL" + type + ". URL=" 
                    + url + " Filter=[no include filters matched]");
        }
    }
    
    private void enhanceHTTPHeaders(Properties metadata) {
        String contentType = metadata.getString("Content-Type");
        if (contentType != null) {
            String mimeType = contentType.replaceFirst("(.*?)(;.*)", "$1");
            String charset = contentType.replaceFirst("(.*?)(; )(.*)", "$3");
            charset = charset.replaceFirst("(charset=)(.*)", "$2");
            metadata.addString(HttpMetadata.DOC_MIMETYPE, mimeType);
            metadata.addString(HttpMetadata.DOC_CHARSET, charset);
        }
    }
    
    private boolean isHeadersRejected() {
        IHttpHeadersFilter[] filters = config.getHttpHeadersFilters();
        if (filters == null) {
            return false;
        }
        HttpMetadata headers = doc.getMetadata();
        boolean hasIncludes = false;
        boolean atLeastOneIncludeMatch = false;
        for (IHttpHeadersFilter filter : filters) {
            boolean accepted = filter.acceptDocument(url, headers);
            boolean isInclude = filter instanceof IOnMatchFilter
                   && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
            if (isInclude) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }
            if (accepted) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "ACCEPTED document http headers. URL=%s Filter=%s",
                            url, filter));
                }
            } else {
                for (IHttpCrawlerEventListener listener : listeners) {
                    listener.documentHeadersRejected(
                            crawler, url, filter, headers);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "REJECTED document http headers. URL=%s Filter=%s",
                            url, filter));
                }
                return true;
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            for (IHttpCrawlerEventListener listener : listeners) {
                listener.documentHeadersRejected(
                        crawler, url, null, headers);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("REJECTED document http headers. "
                        + "URL=%s Filter=(no include filters matched)", url));
            }            
            return true;
        }
        return false;        
    }
    
    private boolean isHeadersChecksumRejected() {
        IHttpHeadersChecksummer check = config.getHttpHeadersChecksummer();
        if (check == null) {
            return false;
        }
        HttpMetadata headers = doc.getMetadata();
        String newHeadChecksum = check.createChecksum(headers);
        crawlURL.setHeadChecksum(newHeadChecksum);
        String oldHeadChecksum = null;
        CrawlURL cachedURL = database.getCached(crawlURL.getUrl());
        if (cachedURL != null) {
            oldHeadChecksum = cachedURL.getHeadChecksum();
        } else {
            LOG.debug("ACCEPTED document headers checkum (new): URL="
                    + crawlURL.getUrl());
            return false;
        }
        if (StringUtils.isNotBlank(newHeadChecksum) 
                && ObjectUtils.equals(newHeadChecksum, oldHeadChecksum)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED document headers checkum (unmodified): URL="
                        + crawlURL.getUrl());
            }
            return true;
        }
        LOG.debug("ACCEPTED document headers checkum (modified): URL=" 
                + crawlURL.getUrl());
        return false;
    }
    
    
}

