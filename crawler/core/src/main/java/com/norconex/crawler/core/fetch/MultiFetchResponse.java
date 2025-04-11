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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.norconex.crawler.core.doc.CrawlDocStatus;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Holds all responses obtained from fetching a document
 * using one or multiple fetchers.
 * @param <T> fetch response type
 */
@Data
@RequiredArgsConstructor
public class MultiFetchResponse<T extends FetchResponse>
        implements FetchResponse {

    @Getter(value = AccessLevel.NONE)
    @Setter(value = AccessLevel.NONE)
    private final List<T> fetchResponses;

    @Override
    public CrawlDocStatus getResolutionStatus() {
        return getLastFetchResponse().map(
                FetchResponse::getResolutionStatus).orElse(null);
    }

    @Override
    public int getStatusCode() {
        return getLastFetchResponse().map(
                FetchResponse::getStatusCode).orElse(0);
    }

    @Override
    public String getReasonPhrase() {
        return getLastFetchResponse().map(
                FetchResponse::getReasonPhrase).orElse(null);
    }

    @Override
    public Exception getException() {
        return getLastFetchResponse().map(
                FetchResponse::getException).orElse(null);
    }

    public List<T> getFetchResponses() {
        return Collections.unmodifiableList(fetchResponses);
    }

    protected Optional<T> getLastFetchResponse() {
        if (fetchResponses.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                fetchResponses.get(fetchResponses.size() - 1));
    }

    @Override
    public String toString() {
        var op = getLastFetchResponse();
        if (!op.isPresent()) {
            return "[No fetch responses.]";
        }

        var r = op.get();
        var b = new StringBuilder(
                r.getStatusCode() + " " + r.getReasonPhrase());
        return b.toString();
    }
}
