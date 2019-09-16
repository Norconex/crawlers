/* Copyright 2010-2019 Norconex Inc.
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
package com.norconex.collector.http.pipeline.importer;

import com.norconex.collector.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.collector.http.reference.HttpCrawlReference;
import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.importer.Importer;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpImporterPipelineContext extends ImporterPipelineContext {

    private RobotsMeta robotsMeta;

    /**
     * Constructor creating a copy of supplied context.
     * @param ipc the item to be copied
     * @since 2.8.0
     */
    public HttpImporterPipelineContext(ImporterPipelineContext ipc) {
        super(ipc.getCrawler());
        BeanUtil.copyProperties(this, ipc);
    }

    public HttpImporterPipelineContext(
            HttpCrawler crawler,
            HttpCrawlReference crawlRef, HttpCrawlReference cachedCrawlRef,
            HttpDocument doc) {
        super(crawler, crawlRef, cachedCrawlRef, doc);
    }

    @Override
    public HttpCrawler getCrawler() {
        return (HttpCrawler) super.getCrawler();
    }

    @Override
    public HttpCrawlerConfig getConfig() {
        return getCrawler().getCrawlerConfig();
    }

    @Override
    public HttpCrawlReference getCrawlReference() {
        return (HttpCrawlReference) super.getCrawlReference();
    }

    @Override
    public HttpCrawlReference getCachedCrawlReference() {
        return (HttpCrawlReference) super.getCachedCrawlReference();
    }

//    public HttpClient getHttpClient() {
//        return getCrawler().getHttpClient();
//    }
    public HttpFetchClient getHttpFetchClient() {
        return getCrawler().getHttpFetchClient();
    }

    @Override
    public HttpDocument getDocument() {
        return (HttpDocument) super.getDocument();
    }

//    public IHttpMetadataFetcher getHttpHeadersFetcher() {
//        return getConfig().getMetadataFetcher();
//    }
//
    public ISitemapResolver getSitemapResolver() {
        return getCrawler().getSitemapResolver();
    }

    public HttpMetadata getMetadata() {
        return getDocument().getMetadata();
    }

    public Importer getImporter() {
        return getCrawler().getImporter();
    }

    public RobotsMeta getRobotsMeta() {
        return robotsMeta;
    }
    /**
     * @param robotsMeta the robotsMeta to set
     */
    public void setRobotsMeta(RobotsMeta robotsMeta) {
        this.robotsMeta = robotsMeta;
    }

    public boolean isHttpHeadFetchEnabled() {
        return getConfig().isFetchHttpHead();
    }

}
