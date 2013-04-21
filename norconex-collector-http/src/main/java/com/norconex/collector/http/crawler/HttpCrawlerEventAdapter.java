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

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.handler.IHttpDocumentFetcher;
import com.norconex.collector.http.handler.IHttpDocumentProcessor;
import com.norconex.collector.http.handler.IHttpHeadersFetcher;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.commons.lang.map.Properties;

/**
 * Adapter for {@link IHttpCrawlerEventListener}.  None of the method
 * implementations do nothing. Subclasing of this class
 * is favoured over direct implementation of {@link IHttpCrawlerEventListener}.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class HttpCrawlerEventAdapter implements IHttpCrawlerEventListener{
    
    public void crawlerStarted(HttpCrawler crawler) {}
    public void documentRobotsTxtRejected(HttpCrawler crawler,
            String url, IURLFilter filter, RobotsTxt robotsTxt) {}
    public void documentURLRejected(HttpCrawler crawler,String url, IURLFilter filter) {}
    public void documentHeadersFetched(HttpCrawler crawler,
            String url, IHttpHeadersFetcher headersFetcher, Properties headers) {}
    public void documentHeadersRejected(HttpCrawler crawler,
            String url, IHttpHeadersFilter filter, Properties headers) {}
    public void documentFetched(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentFetcher fetcher) {}
    public void documentURLsExtracted(HttpCrawler crawler, HttpDocument document) {}
    public void documentRejected(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentFilter filter) {}
    public void documentPreProcessed(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentProcessor preProcessor) {}
    public void documentImported(HttpCrawler crawler,HttpDocument document) {}
    public void documentPostProcessed(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentProcessor postProcessor) {}
    public void crawlerFinished(HttpCrawler crawler) {}
    public void documentCrawled(HttpCrawler crawler, HttpDocument document) {}
}
