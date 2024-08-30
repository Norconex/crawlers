/* Copyright 2022-2024 Norconex Inc.
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

import java.util.function.Function;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetchResponse;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcher;
import com.norconex.crawler.web.util.Web;

//TODO make default and mvoe where crawlsession is constructed?
public class HttpFetcherProvider
        implements Function<Crawler, HttpMultiFetcher> {

    @Override
    public HttpMultiFetcher apply(Crawler crawler) {

        var cfg = (WebCrawlerConfig) crawler.getConfiguration();

        //        var fetchers = (List<HttpFetcher>) cfg.getFetchers();
        //TODO really convert here?  and this way?
        var fetchers = Web.toHttpFetcher(cfg.getFetchers());
        if (fetchers.isEmpty()) {
            fetchers.add(new GenericHttpFetcher());
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
                (state, msg, ex) -> GenericHttpFetchResponse.builder()
                        .crawlDocState(state)
                        .reasonPhrase(msg)
                        .exception(ex)
                        .build(),
                cfg.getFetchersMaxRetries(),
                cfg.getFetchersRetryDelay());
    }
}
