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
package com.norconex.crawler.fs.crawler.impl;

import com.norconex.crawler.core.crawler.CrawlerImpl;
import com.norconex.crawler.fs.doc.FsDocRecord;
import com.norconex.crawler.fs.fetch.FileFetcherProvider;
import com.norconex.crawler.fs.pipeline.committer.FsCommitterPipeline;
import com.norconex.crawler.fs.pipeline.importer.FsImporterPipeline;
import com.norconex.crawler.fs.pipeline.queue.FsQueuePipeline;

public final class FsCrawlerImplFactory {

    private FsCrawlerImplFactory() {}

    public static CrawlerImpl create() {
        return CrawlerImpl.builder()
                .fetcherProvider(new FileFetcherProvider())
                .beforeCrawlerExecution(new BeforeFsCrawlerExecution())
                .queuePipeline(new FsQueuePipeline())
                .importerPipeline(new FsImporterPipeline())
                .committerPipeline(new FsCommitterPipeline())
                .crawlDocRecordType(FsDocRecord.class)
                .docRecordFactory(ctx -> new FsDocRecord(
                        ctx.reference()
                        //TODO What about depth, cached doc, etc? It should be
                        // set here unless this is really used just for queue
                        // initialization or set by caller
                        //, 999
                        ))
                .build();
    }
}
