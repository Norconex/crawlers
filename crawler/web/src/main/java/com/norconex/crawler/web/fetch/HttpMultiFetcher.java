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
package com.norconex.crawler.web.fetch;

import java.time.Duration;
import java.util.List;

import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.fetch.MultiFetcher;

import lombok.NonNull;

/**
 * Extends {@link MultiFetcher} only to offer methods that do not
 * necessitate type casting.
 */
public class HttpMultiFetcher
        extends MultiFetcher<HttpFetchRequest, HttpFetchResponse>
        implements HttpFetcher {

    //TODO now that fetchers are part of core config and is a concept
    // shared by all crawler impls, shall we try to stick
    // to core MultiFetcher ?

    public HttpMultiFetcher(
            @NonNull List<? extends Fetcher<HttpFetchRequest,
                    HttpFetchResponse>> fetchers,
            @NonNull ResponseListAdapter<
                    HttpFetchResponse> multiResponseWrapper,
            @NonNull UnsuccessfulResponseFactory<
                    HttpFetchResponse> unsuccessfulResponseAdaptor,
            int maxRetries, Duration retryDelay
    ) {
        super(
                fetchers,
                multiResponseWrapper,
                unsuccessfulResponseAdaptor,
                maxRetries,
                retryDelay
        );
    }
}
