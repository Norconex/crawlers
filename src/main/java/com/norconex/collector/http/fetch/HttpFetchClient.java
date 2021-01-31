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

import static com.norconex.collector.http.fetch.HttpMethod.GET;
import static java.util.Optional.ofNullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.io.CachedStreamFactory;

/**
 * Fetches HTTP resources, trying all configured http fetchers, defaulting
 * to {@link GenericHttpFetcher} with default configuration if none are defined.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class HttpFetchClient {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpFetchClient.class);

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

    public IHttpFetchResponse fetch(CrawlDoc doc, HttpMethod httpMethod) {

        HttpFetchClientResponse allResponses = new HttpFetchClientResponse();
        boolean accepted = false;
        for (IHttpFetcher fetcher : fetchers) {
            if (!fetcher.accept(doc)) {
                continue;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Fetcher {} accepted this reference: \"{}\".",
                        fetcher.getClass().getSimpleName(), doc.getReference());
            }
            accepted = true;
            for (int retryCount = 0; retryCount <= maxRetries; retryCount++) {
                if (retryCount > 0) {
                    Sleeper.sleepMillis(retryDelay);
                }

                // fetch:
                HttpMethod method = ofNullable(httpMethod).orElse(GET);
                IHttpFetchResponse fetchResponse;
                try {
                    fetchResponse = fetcher.fetch(doc, method);
                } catch (HttpFetchException | RuntimeException e) {
                    LOG.error("Fetcher {} failed to execute request.",
                            fetcher.getClass().getSimpleName(), e);
                    fetchResponse = new HttpFetchResponseBuilder()
                            .setCrawlState(CrawlState.ERROR)
                            .setException(e)
                            .create();
                }
                if (fetchResponse == null) {
                    fetchResponse =
                            HttpFetchResponseBuilder.unsupported().create();
                }
                allResponses.addResponse(fetchResponse, fetcher);

                doc.getMetadata().add(
                        HttpDocMetadata.HTTP_FETCHER,
                        fetcher.getClass().getName());

                if (fetchResponse.getCrawlState() != null
                        && fetchResponse.getCrawlState().isGoodState()) {
                    return allResponses;
                }
            }
        }
        if (!accepted) {
            LOG.warn("No HTTP Fetcher accepted to fetch this "
                    + "reference: \"{}\". "
                    + "For generic URL filtering it is highly recommended you "
                    + "use a regular URL filtering options, such as reference "
                    + "filters.", doc.getReference());
        }
        return allResponses;
    }
}
