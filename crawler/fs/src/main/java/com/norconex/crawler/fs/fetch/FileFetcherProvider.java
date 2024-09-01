/* Copyright 2023-2024 Norconex Inc.
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

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.fs.fetch.impl.GenericFileFetchResponse;
import com.norconex.crawler.fs.fetch.impl.local.LocalFetcher;

import lombok.NonNull;

//TODO make default and move where crawler is constructed?
public class FileFetcherProvider
        implements Function<Crawler, FileMultiFetcher> {

    @Override
    public FileMultiFetcher apply(Crawler crawler) {

        var cfg = crawler.getConfiguration();

        var fetchers = toFileFetchers(cfg.getFetchers());
        if (fetchers.isEmpty()) {
            fetchers = List.of(new LocalFetcher());
        }

        return new FileMultiFetcher(
                fetchers,
                FileMultiFetchResponse::new,
                (state, msg, ex) -> GenericFileFetchResponse.builder()
                        .crawlDocState(state)
                        .reasonPhrase(msg)
                        .exception(ex)
                        .build(),
                cfg.getFetchersMaxRetries(),
                cfg.getFetchersRetryDelay());
    }

    public static List<FileFetcher> toFileFetchers(
            @NonNull Collection<Fetcher<?, ?>> fetchers) {
        return fetchers.stream()
                .map(FileFetcher.class::cast)
                .toList();
    }
}
