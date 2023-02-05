/* Copyright 2022-2023 Norconex Inc.
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.ListOrderedMap;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.doc.CrawlDocState;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @param <T> fetcher request type
 * @param <R> fetcher response type
 */
@Slf4j
public class GenericMultiFetcher
        <T extends FetchRequest, R extends FetchResponse>
            implements MultiFetcher<T, R> {

    private final List<? extends Fetcher<T, R>> fetchers;
    private final MultiResponseAdaptor<T, R> multiResponseAdaptor;
    private final UnsuccessfulResponseAdaptor<R> unsuccessfulResponseAdaptor;

    private final int maxRetries;
    private final long retryDelay;

    @FunctionalInterface
    public interface MultiResponseAdaptor
            <T extends FetchRequest, R extends FetchResponse> {
        //TODO Document that responses are ordered from first to last
        MultiFetchResponse<R> adapt(Map<R, Fetcher<T, R>> responses);
    }

    @FunctionalInterface
    public interface UnsuccessfulResponseAdaptor<R extends FetchResponse> {
        R adapt(CrawlDocState crawlState, String message, Exception e);
    }

    @Builder
    public GenericMultiFetcher(
            @NonNull List<? extends Fetcher<T, R>> fetchers,
            @NonNull MultiResponseAdaptor<T, R> multiResponseAdaptor,
            @NonNull UnsuccessfulResponseAdaptor<R> unsuccessfulResponseAdaptor,
            int maxRetries,
            long retryDelay) {
        if (CollectionUtils.isEmpty(fetchers)) {
            throw new IllegalArgumentException("Need at least 1 fetcher.");
        }
        this.multiResponseAdaptor = multiResponseAdaptor;
        this.unsuccessfulResponseAdaptor = unsuccessfulResponseAdaptor;
        this.fetchers = Collections.unmodifiableList(fetchers);
        this.maxRetries = maxRetries;
        this.retryDelay = retryDelay;
    }

    @Override
    public boolean accept(T fetchRequest) {
        return true;
    }

    /**
     * Fetches a document.
     * @param fetchRequest fetch request
     * @return fetch response
     */
    @Override
    public MultiFetchResponse<R> fetch(T fetchRequest) {

        var doc = (CrawlDoc) fetchRequest.getDoc();

        Map<R, Fetcher<T, R>> allResponses = new ListOrderedMap<>();
        var accepted = false;
        for (Fetcher<T, R> fetcher : fetchers) {
            if (!fetcher.accept(fetchRequest)) {
                continue;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Fetcher {} accepted this reference: \"{}\".",
                        fetcher.getClass().getSimpleName(), doc.getReference());
            }
            accepted = true;
            for (var retryCount = 0; retryCount <= maxRetries; retryCount++) {
                var fetchResponse = doFetch(fetcher, fetchRequest, retryCount);

                allResponses.put(fetchResponse, fetcher);

                doc.getMetadata().add(
                        CrawlDocMetadata.FETCHER, fetcher.getClass().getName());

                if (fetchResponse.getCrawlState() != null
                        && fetchResponse.getCrawlState().isGoodState()) {
                    return multiResponseAdaptor.adapt(allResponses);
                }
                LOG.debug("Fetcher {} response returned a bad crawl "
                        + "state: {}",
                        fetcher.getClass().getSimpleName(),
                        fetchResponse.getCrawlState());
            }
        }
        if (!accepted) {
            allResponses.put(unsuccessfulResponseAdaptor.adapt(
                    CrawlDocState.UNSUPPORTED,
                    "No fetcher defined accepting URL '"
                            + doc.getReference() + "' for fetch request: "
                            + fetchRequest,
                    null), null);
            LOG.debug("""
                No HTTP Fetcher accepted to fetch this\s\
                reference: "{}".\s\
                For generic URL filtering it is highly recommended you\s\
                use a regular URL filtering options, such as reference\s\
                filters.""", doc.getReference());
        }
        return multiResponseAdaptor.adapt(allResponses);
    }

    private R doFetch(
            Fetcher<T, R> fetcher, T fetchRequest, int retryCount) {

        if (retryCount > 0) {
            Sleeper.sleepMillis(retryDelay);
            LOG.debug("Retry attempt #{} to fetch '{}' using '{}'.",
                    retryCount,
                    fetchRequest.getDoc().getReference(),
                    fetcher.getClass().getSimpleName());
        }

        R fetchResponse;
        try {
            fetchResponse = fetcher.fetch(fetchRequest);
        } catch (FetchException | RuntimeException e) {
            LOG.error("Fetcher {} failed to execute request.",
                    fetcher.getClass().getSimpleName(), e);
            fetchResponse = unsuccessfulResponseAdaptor.adapt(
                    CrawlDocState.ERROR, "Fetcher execution failure.", e);
        }
        if (fetchResponse == null) {
            fetchResponse = unsuccessfulResponseAdaptor.adapt(
                    CrawlDocState.UNSUPPORTED,
                    "Fetch operation unsupported by fetcher.",
                    null);
        }
        return fetchResponse;
    }
}