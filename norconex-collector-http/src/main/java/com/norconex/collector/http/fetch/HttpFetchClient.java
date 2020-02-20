/* Copyright 2018-2020 Norconex Inc.
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
package com.norconex.collector.http.fetch;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;

import com.norconex.importer.doc.Doc;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;

/**
 * Fetches HTTP resources, trying all configured http fetchers, defaulting
 * to {@link GenericHttpFetcher} with default configuration if none are defined.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class HttpFetchClient {

    //TODO by default, continue to next if unsupported status is returned.
    //  But have options to configure so that it continue if exception
    // or continue if null

    //TODO Have retry attempts here, or on each fetcher (more control)?

    //TODO check ONCE if user agent is provided and log a warning if not
    // (or leave it to fetcher implementations?

    private final List<IHttpFetcher> fetchers = new ArrayList<>();
    private final CachedStreamFactory streamFactory;
    private final int maxRetries;
    private final long retryDelay;

    public HttpFetchClient(
            CachedStreamFactory streamFactory,
            List<IHttpFetcher> httpFetchers,
            int maxRetries, long retryDelay) {
        Objects.requireNonNull(
                streamFactory, "'streamFactory' must not be null.");
        this.streamFactory = streamFactory;
        if (CollectionUtils.isEmpty(httpFetchers)) {
            this.fetchers.add(new GenericHttpFetcher());
        } else {
            this.fetchers.addAll(httpFetchers);
        }
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
    }

    public CachedStreamFactory getStreamFactory() {
        return streamFactory;
    }


    public IHttpFetchResponse fetchHeaders(String url, Properties headers) {
        return fetch(fetcher -> fetcher.fetchHeaders(url, headers));
    }
    public IHttpFetchResponse fetchDocument(Doc doc) {
        return fetch(fetcher -> fetcher.fetchDocument(doc));
    }

    public Doc fetchDocument(String url) {
        Doc doc = new Doc(url, streamFactory.newInputStream());
        fetch(fetcher -> fetcher.fetchDocument(doc));
        return doc;
    }
    public IHttpFetchResponse fetchDocument(String url, OutputStream out)
            throws HttpFetchException {
        Doc doc = new Doc(url, streamFactory.newInputStream());
        IHttpFetchResponse resp = fetch(fetcher -> fetcher.fetchDocument(doc));
        try {
            IOUtils.copy(doc.getInputStream(), out);
            doc.dispose();
        } catch (IOException e) {
            throw new HttpFetchException("Could not fetch: " + url, e);
        }
        return resp;
    }
//    //TODO how about disposing the input stream here?  shall we have this method?
//    public InputStream fetchDocumentContent(String url) {
//        return fetchDocument(url).getInputStream();
//    }

    private IHttpFetchResponse fetch(
            Function<IHttpFetcher, IHttpFetchResponse> supplier) {
        HttpFetchClientResponse allResponses = new HttpFetchClientResponse();
        for (IHttpFetcher fetcher : fetchers) {
            for (int retryCount = 0; retryCount <= maxRetries; retryCount++) {
                if (retryCount > 0) {
                    Sleeper.sleepMillis(retryDelay);
                }

                // fetch:
                IHttpFetchResponse fetchResponse = supplier.apply(fetcher);
                if (fetchResponse == null) {
                    fetchResponse =
                            HttpFetchResponseBuilder.unsupported().build();
                }
                allResponses.addResponse(fetchResponse, fetcher);

                if (fetchResponse.getCrawlState() != null
                        && fetchResponse.getCrawlState().isGoodState()) {
                    return allResponses;
                }
            }
        }
        return allResponses;
    }
}
