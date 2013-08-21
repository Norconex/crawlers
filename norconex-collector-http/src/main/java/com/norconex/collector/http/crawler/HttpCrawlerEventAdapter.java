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
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.collector.http.fetch.IHttpHeadersFetcher;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.commons.lang.map.Properties;

/**
 * Adapter for {@link IHttpCrawlerEventListener}.  None of the method
 * implementations do nothing. Subclasing of this class
 * is favoured over direct implementation of {@link IHttpCrawlerEventListener}.
 * @author Pascal Essiembre
 * @see HttpCrawlerEventFirer
 */
public class HttpCrawlerEventAdapter implements IHttpCrawlerEventListener{
    
    @Override
    public void crawlerStarted(HttpCrawler crawler) {
        //do nothing
    }
    @Override
    public void documentRobotsTxtRejected(HttpCrawler crawler,
            String url, IURLFilter filter, RobotsTxt robotsTxt) {
        //do nothing
    }
    @Override
    public void documentRobotsMetaRejected(
            HttpCrawler crawler, String url, RobotsMeta robotsMeta) {
        //do nothing
    }
    @Override
    public void documentURLRejected(
            HttpCrawler crawler,String url, IURLFilter filter) {
        //do nothing
    }
    @Override
    public void documentHeadersFetched(HttpCrawler crawler,
            String url, IHttpHeadersFetcher headersFetcher, 
            Properties headers) {
        //do nothing
    }
    @Override
    public void documentHeadersRejected(HttpCrawler crawler,
            String url, IHttpHeadersFilter filter, Properties headers) {
        //do nothing
    }
    @Override
    public void documentFetched(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentFetcher fetcher) {
        //do nothing
    }
    @Override
    public void documentURLsExtracted(
            HttpCrawler crawler, HttpDocument document) {
        //do nothing
    }
    @Override
    public void documentRejected(HttpCrawler crawler,
            HttpDocument document, IHttpDocumentFilter filter) {
        //do nothing
    }
    @Override
    public void documentPreProcessed(HttpCrawler crawler, HttpDocument document,
            IHttpDocumentProcessor preProcessor) {
        //do nothing
    }
    @Override
    public void documentImported(HttpCrawler crawler,HttpDocument document) {
        //do nothing
    }
    @Override
    public void documentImportRejected(HttpCrawler crawler,
            HttpDocument document) {
        // do nothing
    }
    @Override
    public void documentPostProcessed(HttpCrawler crawler, 
            HttpDocument document, IHttpDocumentProcessor postProcessor) {
        //do nothing
    }
    @Override
    public void crawlerFinished(HttpCrawler crawler) {
        //do nothing
    }
    @Override
    public void documentCrawled(HttpCrawler crawler, HttpDocument document) {
        //do nothing
    }
}
