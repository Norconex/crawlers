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
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.norconex.crawler.core.doc.CrawlState;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * Hold response information obtained from fetching a document
 * using a fetch client.
 */
@Data
public class GenericMultiFetchResponse<R extends IFetchResponse>
        implements IMultiFetchResponse<R> {

    @Getter(value = AccessLevel.NONE)
    @Setter(value = AccessLevel.NONE)
    private final List<Pair<R, IFetcher<?, R>>> fetchResponses =
            new ArrayList<>();

    @Override
    public CrawlState getCrawlState() {
        return getLastFetchResponse().map(
                IFetchResponse::getCrawlState).orElse(null);
    }

    @Override
    public int getStatusCode() {
        return getLastFetchResponse().map(
                IFetchResponse::getStatusCode).orElse(0);
    }

    @Override
    public String getReasonPhrase() {
        return getLastFetchResponse().map(
                IFetchResponse::getReasonPhrase).orElse(null);
    }
//    @Override
//    public String getUserAgent() {
//        return lastResponse().map(
//                IFetchResponse::getUserAgent).orElse(null);
//    }
//    @Override
    @Override
    public Exception getException() {
        return getLastFetchResponse().map(
                IFetchResponse::getException).orElse(null);
    }
//    @Override
//    public String getRedirectTarget() {
//        return lastResponse().map(
//                IFetchResponse::getRedirectTarget).orElse(null);
//    }

    @Override
    public List<R> getFetchResponses() {
        return fetchResponses.stream().map(
                Pair::getLeft).collect(Collectors.toList());
    }

    @Override
    public void addFetchResponse(R resp, IFetcher<?, R> fetcher) {
        fetchResponses.add(0, new ImmutablePair<>(resp, fetcher));
    }

    @Override
    public Optional<R> getLastFetchResponse() {
        if (fetchResponses.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(fetchResponses.get(0).getLeft());
    }
    private Optional<IFetcher<?, ?>> lastFetcher() {
        if (fetchResponses.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(fetchResponses.get(0).getRight());
    }

    @Override
    public String toString() {
        Optional<R> op = getLastFetchResponse();
        if (!op.isPresent()) {
            return "[No fetch responses.]";
        }

        R r = op.get();
        StringBuilder b = new StringBuilder(
                r.getStatusCode()  + " " + r.getReasonPhrase());
        lastFetcher().ifPresent(f -> b.append(
                " - " + f.getClass().getSimpleName()));
        return b.toString();
    }
}
