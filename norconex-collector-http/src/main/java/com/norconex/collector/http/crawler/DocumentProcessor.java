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
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.IHttpHeadersFetcher;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.handler.IHttpDocumentChecksummer;
import com.norconex.collector.http.handler.IHttpDocumentProcessor;
import com.norconex.collector.http.handler.IHttpHeadersChecksummer;
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
 * Performs the document processing.  
 * Instances are only valid for the scope of a single URL.  
 * @author Pascal Essiembre
 */
public final class DocumentProcessor {

    private static final Logger LOG = 
            LogManager.getLogger(DocumentProcessor.class);
    
    private final HttpCrawler crawler;
    private final HttpCrawlerConfig config;
    private final List<IHttpCrawlerEventListener> listeners = 
            new ArrayList<IHttpCrawlerEventListener>();
    private final CrawlURL crawlURL;
    private final DefaultHttpClient httpClient;
    private final HttpDocument doc;
    private final IHttpHeadersFetcher hdFetcher;
    private final ICrawlURLDatabase database;
    private final File outputFile;
    private RobotsTxt robotsTxt;
    private RobotsMeta robotsMeta;
    
    // Order is important.
    private final IURLProcessingStep[] steps = new IURLProcessingStep[] {
        new DelayResolverStep(),
        new HttpHeadersFetcherHEADStep(),
        new HttpHeadersFiltersHEADStep(),
        new HttpHeadersChecksumHEADStep(),
        new DocumentFetcherStep(),
        new RobotsMetaCreateStep(),
        new URLExtractorStep(),
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

    public DocumentProcessor(
            HttpCrawler crawler, DefaultHttpClient httpClient, 
            ICrawlURLDatabase database, File outputFile,
            HttpDocument doc, CrawlURL crawlURL) {
        this.crawler = crawler;
        this.httpClient = httpClient;
        this.database = database;
        this.doc = doc;
        this.crawlURL = crawlURL;
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

    //--- Wait for delay to expire ---------------------------------------------
    private class DelayResolverStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            IDelayResolver delayResolver = config.getDelayResolver();
            if (delayResolver != null) {
                synchronized (delayResolver) {
                    delayResolver.delay(robotsTxt, crawlURL.getUrl());
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
                Properties metadata = hdFetcher.fetchHTTPHeaders(
                        httpClient, crawlURL.getUrl());
                if (metadata == null) {
                    crawlURL.setStatus(CrawlStatus.REJECTED);
                    return false;
                }
                doc.getMetadata().putAll(metadata);
                enhanceHTTPHeaders(doc.getMetadata());
                for (IHttpCrawlerEventListener listener : listeners) {
                    listener.documentHeadersFetched(
                            crawler, crawlURL.getUrl(), 
                            hdFetcher, doc.getMetadata());
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
                            reader, crawlURL.getUrl(),
                            doc.getMetadata().getContentType(),
                            doc.getMetadata());
                    reader.close();
                } catch (IOException e) {
                    throw new HttpCollectorException(
                            "Cannot create RobotsMeta for URL: " 
                                    + crawlURL.getUrl(), e);
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
                          + "rule: " + crawlURL.getUrl());
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
                            + "for URL: " + crawlURL.getUrl());
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
                        "Cannot extract URLs from: " + crawlURL.getUrl(), e);
            }

            Set<String> uniqueURLs = new HashSet<String>();
            if (urls != null) {
                for (String url : urls) {
                    CrawlURL newURL = 
                            new CrawlURL(url, crawlURL.getDepth() + 1);
                    if (new URLProcessor(crawler, httpClient, 
                            database, newURL).processURL()) {
                        uniqueURLs.add(newURL.getUrl());
                    }
                }
            }
            if (!uniqueURLs.isEmpty()) {
                doc.getMetadata().addString(HttpMetadata.REFERNCED_URLS, 
                        uniqueURLs.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
            }
            for (IHttpCrawlerEventListener listener : listeners) {
                listener.documentURLsExtracted(crawler, doc);
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
                        "Cannot import URL: " + crawlURL.getUrl(), e);
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
                committer.queueAdd(
                        crawlURL.getUrl(), outputFile, doc.getMetadata());
            }
            for (IHttpCrawlerEventListener listener : listeners) {
                listener.documentCrawled(crawler, doc);
            }
            return true;
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
            boolean accepted = filter.acceptDocument(
                    crawlURL.getUrl(), headers);
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
                            crawlURL.getUrl(), filter));
                }
            } else {
                for (IHttpCrawlerEventListener listener : listeners) {
                    listener.documentHeadersRejected(
                            crawler, crawlURL.getUrl(), filter, headers);
                }
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "REJECTED document http headers. URL=%s Filter=%s",
                            crawlURL.getUrl(), filter));
                }
                return true;
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            for (IHttpCrawlerEventListener listener : listeners) {
                listener.documentHeadersRejected(
                        crawler, crawlURL.getUrl(), null, headers);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("REJECTED document http headers. "
                        + "URL=%s Filter=(no include filters matched)", 
                                crawlURL.getUrl()));
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

