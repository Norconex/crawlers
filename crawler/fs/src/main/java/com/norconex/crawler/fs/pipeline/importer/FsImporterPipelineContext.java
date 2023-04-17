/* Copyright 2013-2017 Norconex Inc.
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
package com.norconex.crawler.fs.pipeline.importer;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;

/**
 * @author Pascal Essiembre
 *
 */
public class FsImporterPipelineContext extends ImporterPipelineContext {

//    private FileObject fileObject;

    /**
     * Constructor creating a copy of supplied context.
     * @param ipc the item to be copied
     * @since 2.7.2
     */
    public FsImporterPipelineContext(ImporterPipelineContext ipc) {
        super(ipc.getCrawler(), ipc.getDocument());
        BeanUtil.copyProperties(this, ipc);
    }

    public FsImporterPipelineContext(Crawler crawler, CrawlDoc doc) {
        super(crawler, doc);

//            FileDocument doc,
//            BaseCrawlData crawlData,
//            BaseCrawlData cachedCrawlData,
//            FileObject fileObject) {
//        super(crawler, crawlDataStore, crawlData, cachedCrawlData, doc);
//        this.fileObject = fileObject;
    }

    @Override
    public CrawlerConfig getConfig() {
        return getCrawler().getCrawlerConfig();
    }

//    public FileObject getFileObject() {
//        return fileObject;
//    }
//    /**
//     * Sets file object.
//     * @param fileObject file object
//     * @since 2.7.2
//     */
//    public void setFileObject(FileObject fileObject) {
//        this.fileObject = fileObject;
//    }
//
//    public FileMetadata getMetadata() {
//        return getDocument().getMetadata();
//    }

}
