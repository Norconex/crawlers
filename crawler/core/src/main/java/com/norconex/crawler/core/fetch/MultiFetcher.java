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
package com.norconex.crawler.core.fetch;

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.doc.CrawlDocMetaConstants;
import com.norconex.crawler.core.doc.CrawlDocStatus;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @param <T> fetcher request type
 * @param <R> fetcher response type
 */
@Slf4j
public class MultiFetcher<T extends FetchRequest, R extends FetchResponse>
        implements Fetcher<T, R> {

    private final List<? extends Fetcher<T, R>> fetchers;

    private final ResponseListAdapter<R> responseListAdapter;
    private final UnsuccessfulResponseFactory<R> unsuccessfulResponseFactory;

    @Getter
    private final int maxRetries;
    @Getter
    private final Duration retryDelay;

    @FunctionalInterface
    public interface UnsuccessfulResponseFactory<R> {
        R create(CrawlDocStatus crawlState, String message, Exception e);
    }

    // this gives a chance to wrap the MultiFetchResponse into
    // a type appropriate to crawler impl
    // By default, no wrapping is done.
    @FunctionalInterface
    public interface ResponseListAdapter<M extends FetchResponse> {
        M adapt(List<M> multiResponse);
    }

    @Builder
    public MultiFetcher(
            @NonNull List<? extends Fetcher<T, R>> fetchers,
            @NonNull ResponseListAdapter<R> responseListAdapter,
            @NonNull UnsuccessfulResponseFactory<R> unsuccessfulResponseAdaptor,
            int maxRetries,
            Duration retryDelay) {
        if (CollectionUtils.isEmpty(fetchers)) {
            throw new IllegalArgumentException("Need at least 1 fetcher.");
        }
        this.responseListAdapter = responseListAdapter;
        this.unsuccessfulResponseFactory = unsuccessfulResponseAdaptor;
        this.fetchers = Collections.unmodifiableList(fetchers);
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
    }

    public List<Fetcher<T, R>> getFetchers() {
        return Collections.unmodifiableList(fetchers);
    }

    @Override
    public boolean accept(T fetchRequest) {
        // Each one will be tested individually in fetch method.
        return true;
    }

    /**
     * Fetches a document.
     * @param fetchRequest fetch request
     * @return fetch response
     */
    @Override
    public R fetch(T fetchRequest) {

        var doc = fetchRequest.getDoc();

        List<R> allResponses = new ArrayList<>();
        var accepted = false;
        for (Fetcher<T, R> fetcher : fetchers) {
            if (!fetcher.accept(fetchRequest)) {
                continue;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "Fetcher {} accepted this reference: \"{}\".",
                        fetcher.getClass().getSimpleName(), doc.getReference());
            }
            accepted = true;
            for (var retryCount = 0; retryCount <= maxRetries; retryCount++) {
                var fetchResponse = doFetch(fetcher, fetchRequest, retryCount);
                allResponses.add(fetchResponse);

                doc.getMetadata().add(
                        CrawlDocMetaConstants.FETCHER,
                        fetcher.getClass().getName());

                if (fetchResponse.getResolutionStatus() != null
                        && fetchResponse.getResolutionStatus().isGoodState()) {
                    return responseListAdapter.adapt(allResponses);
                }
                LOG.debug(
                        "Fetcher {} response returned a bad crawl "
                                + "state: {}",
                        fetcher.getClass().getSimpleName(),
                        fetchResponse.getResolutionStatus());
            }
        }
        if (!accepted) {
            //            allResponses.put(unsuccessfulResponseAdaptor.create(
            allResponses.add(
                    unsuccessfulResponseFactory.create(
                            CrawlDocStatus.UNSUPPORTED,
                            "No fetcher defined accepting reference '"
                                    + doc.getReference()
                                    + "' for fetch request: "
                                    + fetchRequest,
                            null));
            LOG.debug("""
                No fetcher accepted to fetch this\s\
                reference: "{}".\s\
                For generic reference filtering it is highly recommended you\s\
                use a regular reference filtering options, such as reference\s\
                filters.""",
                    doc.getReference());
        }
        return responseListAdapter.adapt(allResponses);
    }

    private R doFetch(
            Fetcher<T, R> fetcher, T fetchRequest, int retryCount) {

        if (retryCount > 0) {
            Sleeper.sleepMillis(
                    ofNullable(retryDelay).orElse(Duration.ZERO).toMillis());
            LOG.debug(
                    "Retry attempt #{} to fetch '{}' using '{}'.",
                    retryCount,
                    fetchRequest.getDoc().getReference(),
                    fetcher.getClass().getSimpleName());
        }

        R fetchResponse;
        try {
            fetchResponse = fetcher.fetch(fetchRequest);
        } catch (FetchException | RuntimeException e) {
            LOG.error(
                    "Fetcher {} failed to execute request.",
                    fetcher.getClass().getSimpleName(), e);
            fetchResponse = unsuccessfulResponseFactory.create(
                    CrawlDocStatus.ERROR, "Fetcher execution failure.", e);
        }
        if (fetchResponse == null) {
            fetchResponse = unsuccessfulResponseFactory.create(
                    CrawlDocStatus.UNSUPPORTED,
                    "Fetch operation unsupported by fetcher.",
                    null);
        }
        return fetchResponse;
    }
}
