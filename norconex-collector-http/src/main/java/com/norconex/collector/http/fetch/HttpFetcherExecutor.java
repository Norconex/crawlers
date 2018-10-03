/* Copyright 2018 Norconex Inc.
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

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.commons.lang.io.CachedStreamFactory;

/**
 * Fetches HTTP resources, trying all configured http fetchers, defaulting
 * to {@link GenericHttpFetcher} with default configuration if none are defined.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class HttpFetcherExecutor {

    //TODO Have retry attempts here, or on each fetcher (more control)?

    //TODO check ONCE if user agent is provided and log a warning if not
    // (or leave it to fetcher implementations?

    private final List<IHttpFetcher> fetchers = new ArrayList<>();
    private final CachedStreamFactory streamFactory;

    public HttpFetcherExecutor(
            CachedStreamFactory streamFactory,
            List<IHttpFetcher> httpFetchers) {
        Objects.requireNonNull(
                streamFactory, "'streamFactory' must not be null.");
        this.streamFactory = streamFactory;
        if (CollectionUtils.isEmpty(httpFetchers)) {
            this.fetchers.add(new GenericHttpFetcher());
        } else {
            this.fetchers.addAll(httpFetchers);
        }
    }

    public CachedStreamFactory getStreamFactory() {
        return streamFactory;
    }

    public HttpFetchResponse fetchHeaders(String url, HttpMetadata headers) {
        return fetch(fetcher -> fetcher.fetchHeaders(url, headers));
    }
    public HttpFetchResponse fetchDocument(HttpDocument doc) {
        return fetch(fetcher -> fetcher.fetchDocument(doc));
    }
    public HttpDocument fetchDocument(String url) {
        HttpDocument doc = new HttpDocument(url, streamFactory);
        fetch(fetcher -> fetcher.fetchDocument(doc));
        return doc;
    }

    //TODO add a method to IHttpFetcher that simply return a normal input
    // stream?

    public HttpFetchResponse fetchDocumentContent(String url, OutputStream out)
            throws IOException {
        HttpDocument doc = new HttpDocument(url, streamFactory);
        HttpFetchResponse resp = fetch(fetcher -> fetcher.fetchDocument(doc));
        IOUtils.copy(doc.getInputStream(), out);
        doc.dispose();
        return resp;
    }
//    //TODO how about disposing the input stream here?  shall we have this method?
//    public InputStream fetchDocumentContent(String url) {
//        return fetchDocument(url).getInputStream();
//    }

    private HttpFetchResponse fetch(
            Function<IHttpFetcher, HttpFetchResponse> supplier) {
        HttpFetchResponse response = null;
        for (IHttpFetcher fetcher : fetchers) {
//System.out.println("XXXXXXX FETCHER: " + fetcher);
            response = supplier.apply(fetcher);
            if (response != null /*&& response.getCrawlState() == OK*/) {
                response.setFetcher(fetcher.getClass());
                response.setUserAgent(fetcher.getUserAgent());
                break;
            }
        }
//System.out.println("XXXXXXX RESPONSE: " + response);
        return response;
    }
}
