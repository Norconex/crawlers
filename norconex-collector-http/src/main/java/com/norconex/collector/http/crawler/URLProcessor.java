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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.ref.ReferenceState;
import com.norconex.collector.core.ref.store.IReferenceStore;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.collector.http.sitemap.SitemapURLAdder;
import com.norconex.importer.handler.filter.IOnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * Performs a URL handling logic before actual processing of the document
 * it represents takes place.  That is, before any 
 * document or document header download is
 * Instances are only valid for the scope of a single URL.  
 * @author Pascal Essiembre
 */
public final class URLProcessor {

    private static final Logger LOG = 
            LogManager.getLogger(URLProcessor.class);
    
//    private static final List<String> SITEMAP_RESOLVED = 
//            new ArrayList<String>();

    private final HttpCrawler crawler;
    private final HttpCrawlerConfig config;
    private final List<IHttpCrawlerEventListener> listeners = 
            new ArrayList<IHttpCrawlerEventListener>();
    private final HttpDocReference httpDocReference;
    private final HttpClient httpClient;
    private final IReferenceStore refStore;
    private final ISitemapResolver sitemapResolver;
    private RobotsTxt robotsTxt;
    private RobotsMeta robotsMeta;
    private ReferenceState status;
    
    // Order is important.  E.g. Robots must be after URL Filters and before 
    // Delay resolver
    private final IURLProcessingStep[] defaultSteps = new IURLProcessingStep[] {
        new DepthValidationStep(),
        new URLFiltersStep(),
        new RobotsTxtFiltersStep(),
        new URLNormalizerStep(),
        new SitemapStep(),
        new StoreNextURLStep(),
    };
    private final IURLProcessingStep[] sitemapSteps = new IURLProcessingStep[] {
        new DepthValidationStep(),
        new URLFiltersStep(),
        new RobotsTxtFiltersStep(),
        new URLNormalizerStep(),
        new StoreNextURLStep(),
    };

    public URLProcessor(
            HttpCrawler crawler, HttpClient httpClient, 
            IReferenceStore refStore, HttpDocReference baseURL,
            ISitemapResolver sitemapResolver) {
        this.crawler = crawler;
        this.sitemapResolver = sitemapResolver;
        this.httpClient = httpClient;
        this.refStore = refStore;
        this.config = crawler.getCrawlerConfig();
        this.httpDocReference = baseURL;
        IHttpCrawlerEventListener[] ls = config.getCrawlerListeners();
        if (ls != null) {
            this.listeners.addAll(Arrays.asList(ls));
        }
    }

    public boolean processURL() {
        return processURL(defaultSteps);
    }
    private boolean processSitemapURL() {
        return processURL(sitemapSteps);
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
                    && httpDocReference.getDepth() > config.getMaxDepth()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("URL too deep to process (" 
                            + httpDocReference.getDepth() + "): " + httpDocReference.getReference());
                }
                status = HttpDocReferenceState.TOO_DEEP;
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
                status = HttpDocReferenceState.REJECTED;
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
                        httpClient, httpDocReference.getReference(), config.getUserAgent());
                if (isURLRejected(robotsTxt.getFilters(), robotsTxt)) {
                    status = HttpDocReferenceState.REJECTED;
                    return false;
                }
            }
            return true;
        }
    }

    //--- Sitemap URL Extraction -----------------------------------------------
    private class SitemapStep implements IURLProcessingStep {
        @Override
        public synchronized boolean processURL() {
            if (config.isIgnoreSitemap()) {
                return true;
            }
            String urlRoot = httpDocReference.getUrlRoot();
            String[] robotsTxtLocations = null;
            if (robotsTxt != null) {
                robotsTxtLocations = robotsTxt.getSitemapLocations();
            }
            SitemapURLAdder urlStore = new SitemapURLAdder() {
                private static final long serialVersionUID = 
                        7618470895330355434L;
                @Override
                public void add(HttpDocReference baseURL) {
                    new URLProcessor(crawler, httpClient, refStore, baseURL, 
                            sitemapResolver).processSitemapURL();
                }
            };
            sitemapResolver.resolveSitemaps(httpClient, urlRoot, 
                    robotsTxtLocations, urlStore);
            return true;
        }
    }
    

    
    //--- URL Normalizer -------------------------------------------------------
    /*
     * Normalize the URL.
     */
    private class URLNormalizerStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (config.getUrlNormalizer() != null) {
                String url = config.getUrlNormalizer().normalizeURL(
                        httpDocReference.getReference());
                if (url == null) {
                    status = HttpDocReferenceState.REJECTED;
                    return false;
                }
                httpDocReference.setReference(url);
            }
            return true;
        }
    }

    //--- Store Next URLs to process -------------------------------------------
    private class StoreNextURLStep implements IURLProcessingStep {
        @Override
        public boolean processURL() {
            if (robotsMeta != null && robotsMeta.isNofollow()) {
                return true;
            }
            String url = httpDocReference.getReference();
            if (StringUtils.isBlank(url)) {
                return true;
            }
            if (refStore.isActive(url)) {
                debug("Already being processed: %s", url);
            } else if (refStore.isQueued(url)) {
                debug("Already queued: %s", url);
            } else if (refStore.isProcessed(url)) {
                debug("Already processed: %s", url);
            } else {
                refStore.queue(new HttpDocReference(url, httpDocReference.getDepth()));
                debug("Queued for processing: %s", url);
            }
            return true;
        }
    }
    
    private static void debug(String message, Object... values) {
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format(message, values));
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
            boolean accepted = filter.acceptURL(httpDocReference.getReference());
            
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
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ACCEPTED document URL" + type 
                            + ". URL=" + httpDocReference.getReference()
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
                        crawler, httpDocReference.getReference(), filter, robots);
            } else {
                listener.documentURLRejected(
                        crawler, httpDocReference.getReference(), filter);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("REJECTED document URL" + type + ". URL=" 
                  + httpDocReference.getReference() + " Filter=[one or more filter 'onMatch' "
                  + "attribute is set to 'include', but none of them were "
                  + "matched]");
        }
    }
    
    private boolean processURL(IURLProcessingStep... steps) {
        try {
            for (int i = 0; i < steps.length; i++) {
                IURLProcessingStep step = steps[i];
                if (!step.processURL()) {
                    return false;
                }
            }
            status = HttpDocReferenceState.OK;
            return true;
        } catch (Exception e) {
            //TODO do we really want to catch anything other than 
            // HTTPFetchException?  In case we want special treatment to the 
            // class?
            status = HttpDocReferenceState.ERROR;
            LOG.error("Could not process URL: " + httpDocReference.getReference(), e);
            return false;
        } finally {
            //--- Mark URL as Processed ----------------------------------------
            if (status != HttpDocReferenceState.OK) {
                if (status == null) {
                    status = HttpDocReferenceState.BAD_STATUS;
                }
                httpDocReference.setState(status);
                refStore.processed(httpDocReference);
//                status.logInfo(httpDocReference);
            }
        }
    }

    private boolean isIncludeFilter(IURLFilter filter) {
        return filter instanceof IOnMatchFilter
                && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
    }
}

