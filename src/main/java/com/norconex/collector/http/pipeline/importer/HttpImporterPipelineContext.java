/* Copyright 2010-2021 Norconex Inc.
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

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.map.Properties;
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
        super(ipc.getCrawler(), ipc.getDocument());
        BeanUtil.copyProperties(this, ipc);
    }

    public HttpImporterPipelineContext(
            HttpCrawler crawler,
            CrawlDoc doc) {
        super(crawler, doc);
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
    public HttpDocInfo getDocInfo() {
        return (HttpDocInfo) super.getDocInfo();
    }

    @Override
    public HttpDocInfo getCachedDocInfo() {
        return (HttpDocInfo) super.getCachedDocInfo();
    }

    public HttpFetchClient getHttpFetchClient() {
        return getCrawler().getHttpFetchClient();
    }

    public ISitemapResolver getSitemapResolver() {
        return getCrawler().getSitemapResolver();
    }

    public Properties getMetadata() {
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
}
