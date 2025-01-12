/* Copyright 2022-2025 Norconex Inc.
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
package com.norconex.crawler.web.fetch;

import java.util.ArrayList;
import java.util.function.Function;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpClientFetchResponse;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpClientFetcher;

public class HttpFetcherProvider
        implements Function<CrawlerContext, HttpMultiFetcher> {

    @Override
    public HttpMultiFetcher apply(CrawlerContext crawler) {

        var cfg = (WebCrawlerConfig) crawler.getConfiguration();

        //TODO really convert here?  and this way?
        var fetchers = new ArrayList<>(cfg.getFetchers().stream()
                .map(HttpFetcher.class::cast)
                .toList());
        if (fetchers.isEmpty()) {
            fetchers.add(new HttpClientFetcher());
        }

        //TODO REFACTOR since MultiFetcher is the one dealing with multiple
        // fetcher, we do not need this provider anymore.
        // We do need the adaptors though.

        //TODO either find a way to return HttpFetcher instead here,
        // or remove HttpFetcher and pass Fetcher<..., ...> every where
        // (ugly)

        return new HttpMultiFetcher(
                fetchers,
                HttpMultiFetchResponse::new,
                (state, msg, ex) -> HttpClientFetchResponse.builder()
                        .resolutionStatus(state)
                        .reasonPhrase(msg)
                        .exception(ex)
                        .build(),
                cfg.getFetchersMaxRetries(),
                cfg.getFetchersRetryDelay());
    }
}
