/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.crawler.web.pipeline.importer;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.web.crawler.HttpCrawlerConfig;
import com.norconex.crawler.web.crawler.HttpCrawlerConfig.HttpMethodSupport;
import com.norconex.crawler.web.doc.HttpDocRecord;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.robot.RobotsMeta;

import lombok.Data;

@Data
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

    public HttpImporterPipelineContext(Crawler crawler, CrawlDoc doc) {
        super(crawler, doc);
    }

//    @Override
//    public HttpCrawler getCrawler() {
//        return (HttpCrawler) super.getCrawler();
//    }
//
    @Override
    public HttpCrawlerConfig getConfig() {
        return (HttpCrawlerConfig) getCrawler().getCrawlerConfig();
    }

    @Override
    public HttpDocRecord getDocRecord() {
        return (HttpDocRecord) super.getDocRecord();
    }

    @Override
    public HttpDocRecord getCachedDocRecord() {
        return (HttpDocRecord) super.getCachedDocRecord();
    }

    /**
     * Whether a HTTP HEAD request was performed already. Based on whether
     * HTTP HEAD requests are enabled via configuration
     * and we are now doing a GET request (which suggests HEAD would have
     * been performed).
     * @param method HTTP method we are about to invoke
     * @return <code>true</code> if method is GET and HTTP HEAD was performed
     */
    protected boolean wasHttpHeadPerformed(HttpMethod method) {
        // If GET and fetching HEAD was requested, we ran filters already, skip.
        return method == HttpMethod.GET
                &&  HttpMethodSupport.isEnabled(getConfig().getFetchHttpHead());
    }

    /**
     * Check if the supplied method has been enabled via configuration.
     * That is, its use is either "required" or "optional".
     * @param method HTTP method
     * @return <code>true</code> if the supplied method is enabled
     */
    public boolean isHttpMethodEnabled(HttpMethod method) {
        return (method == HttpMethod.HEAD
                && HttpMethodSupport.isEnabled(
                        getConfig().getFetchHttpHead()))
                || (method == HttpMethod.GET
                        && HttpMethodSupport.isEnabled(
                                getConfig().getFetchHttpGet()));
    }

//
////    public HttpFetchClient getHttpFetchClient() {
////        return getCrawler().getHttpFetchClient();
////    }
//    public HttpFetcher getFetcher() {
//        return (HttpFetcher) getCrawler().getFetcher();
//    }
//
//    public SitemapResolver getSitemapResolver() {
////        return getCrawler().getSitemapResolver();
//        return null;
//    }
//
//    public Properties getMetadata() {
//        return getDocument().getMetadata();
//    }
//
//    public Importer getImporter() {
//        return getCrawler().getImporter();
//    }
//
//    public RobotsMeta getRobotsMeta() {
//        return robotsMeta;
//    }
//    /**
//     * @param robotsMeta the robotsMeta to set
//     */
//    public void setRobotsMeta(RobotsMeta robotsMeta) {
//        this.robotsMeta = robotsMeta;
//    }
}
