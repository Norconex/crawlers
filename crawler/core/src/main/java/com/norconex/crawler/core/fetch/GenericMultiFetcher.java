/* Copyright 2022-2022 Norconex Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.ListOrderedMap;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.commons.lang.Sleeper;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * @param <T> fetcher request type
 * @param <R> fetcher response type
 */
@Slf4j
public class GenericMultiFetcher
        <T extends IFetchRequest, R extends IFetchResponse>
            implements IMultiFetcher<T, R> {

    private final List<IFetcher<T, R>> fetchers =
            new ArrayList<>();
    private final IMultiResponseAdaptor<T, R> multiResponseAdaptor;
    private final IUnsuccessfulResponseAdaptor<R> unsuccessfulResponseAdaptor;

    private final int maxRetries;
    private final long retryDelay;

    @FunctionalInterface
    public interface IMultiResponseAdaptor
            <T extends IFetchRequest, R extends IFetchResponse> {
        //TODO Document responses are ordered from first to last
        IMultiFetchResponse<R> adapt(Map<R, IFetcher<T, R>> responses);
    }

    @FunctionalInterface
    public interface IUnsuccessfulResponseAdaptor<R extends IFetchResponse> {
        R adapt(CrawlDocState crawlState, String message, Exception e);
    }

    public GenericMultiFetcher(
            List<? extends IFetcher<T, R>> fetchers,
            @NonNull IMultiResponseAdaptor<T, R> multiResponseAdaptor,
            @NonNull IUnsuccessfulResponseAdaptor<R> unsuccessfulResponseAdaptor,
            int maxRetries,
            long retryDelay) {
        if (CollectionUtils.isEmpty(fetchers)) {
            throw new IllegalArgumentException("Need at least 1 fetcher.");
        }
        this.multiResponseAdaptor = multiResponseAdaptor;
        this.unsuccessfulResponseAdaptor = unsuccessfulResponseAdaptor;
        this.fetchers.addAll(fetchers);
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
    public IMultiFetchResponse<R> fetch(T fetchRequest) {

        CrawlDoc doc = (CrawlDoc) fetchRequest.getDoc();

        Map<R, IFetcher<T, R>> allResponses = new ListOrderedMap<>();
        boolean accepted = false;
        for (IFetcher<T, R> fetcher : fetchers) {
            if (!fetcher.accept(fetchRequest)) {
                continue;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Fetcher {} accepted this reference: \"{}\".",
                        fetcher.getClass().getSimpleName(), doc.getReference());
            }
            accepted = true;
            for (int retryCount = 0; retryCount <= maxRetries; retryCount++) {
                R fetchResponse = doFetch(fetcher, fetchRequest, retryCount);

                allResponses.put(fetchResponse, fetcher);

                doc.getMetadata().add(
                        CrawlDocMetadata.FETCHER, fetcher.getClass().getName());

                if (fetchResponse.getCrawlState() != null
                        && fetchResponse.getCrawlState().isGoodState()) {
                    return multiResponseAdaptor.adapt(allResponses);
                }
            }
        }
        if (!accepted) {
            allResponses.put(unsuccessfulResponseAdaptor.adapt(
                    CrawlDocState.UNSUPPORTED,
                    "No HTTP fetcher defined accepting URL '"
                            + doc.getReference() + "' for fetch request: "
                            + fetchRequest,
                    null), null);
            LOG.debug("No HTTP Fetcher accepted to fetch this "
                    + "reference: \"{}\". "
                    + "For generic URL filtering it is highly recommended you "
                    + "use a regular URL filtering options, such as reference "
                    + "filters.", doc.getReference());
        }
        return multiResponseAdaptor.adapt(allResponses);
    }

    private R doFetch(
            IFetcher<T, R> fetcher, T fetchRequest, int retryCount) {

        if (retryCount > 0) {
            Sleeper.sleepMillis(retryDelay);
            LOG.debug("Retry attempt #{} to fetch '{}' using fetcher '{}'.",
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