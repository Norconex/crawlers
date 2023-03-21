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
package com.norconex.crawler.fs.fetch;

import java.util.function.Function;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.fs.crawler.FsCrawlerConfig;
import com.norconex.crawler.fs.fetch.impl.GenericFileFetchResponse;

//TODO make default and move where crawlsession is constructed?
public class FileFetcherProvider
        implements Function<Crawler, FileMultiFetcher> {

    @Override
    public FileMultiFetcher apply(Crawler crawler) {

        var cfg = (FsCrawlerConfig) crawler.getCrawlerConfig();

        var fetchers = cfg.getFileFetchers();
        if (fetchers.isEmpty()) {
            //fetchers.add(new GenericHttpFetcher());
        }

        return new FileMultiFetcher(
                fetchers,
                FileMultiFetchResponse::new,
                (state, msg, ex) -> GenericFileFetchResponse.builder()
                        .crawlDocState(state)
                        .reasonPhrase(msg)
                        .exception(ex)
                        .build(),
                cfg.getFileFetchersMaxRetries(),
                cfg.getFileFetchersRetryDelay());
    }
}
