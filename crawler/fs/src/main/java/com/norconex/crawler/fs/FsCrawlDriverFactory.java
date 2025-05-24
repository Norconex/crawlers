/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.fs;

import java.util.function.Supplier;

import com.norconex.crawler.core.CrawlCallbacks;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.CrawlDriver.FetchDriver;
import com.norconex.crawler.fs.callbacks.BeforeFsCommand;
import com.norconex.crawler.fs.doc.FsCrawlDocContext;
import com.norconex.crawler.fs.doc.pipelines.FsPipelines;
import com.norconex.crawler.fs.fetch.AggregatedFileFetchResponse;
import com.norconex.crawler.fs.fetch.AggregatedFolderPathsResponse;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.GenericFileFetchResponse;

public class FsCrawlDriverFactory implements Supplier<CrawlDriver> {

    public static CrawlDriver create() {
        return new FsCrawlDriverFactory().get();
    }

    @Override
    public CrawlDriver get() {
        return CrawlDriver.builder()
                .fetchDriver(createFetchDriver())
                .callbacks(CrawlCallbacks.builder()
                        .beforeCommand(new BeforeFsCommand())
                        .build())
                .docPipelines(FsPipelines.create())
                .docContextType(FsCrawlDocContext.class)
                .build();
    }

    private static FetchDriver createFetchDriver() {
        return new FetchDriver()
                .responseAggregator(
                        (req, resps) -> (req instanceof FileFetchRequest
                                ? new AggregatedFileFetchResponse(resps)
                                : new AggregatedFolderPathsResponse(resps)))
                .unsuccesfulResponseFactory(
                        (state, msg, e) -> GenericFileFetchResponse
                                .builder()
                                .resolutionStatus(state)
                                .reasonPhrase(msg)
                                .exception(e)
                                .build());
    }
}
