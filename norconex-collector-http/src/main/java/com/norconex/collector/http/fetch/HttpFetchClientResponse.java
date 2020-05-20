/* Copyright 2019-2020 Norconex Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.builder.ToStringSummary;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.norconex.collector.core.doc.CrawlState;

/**
 * Hold HTTP response information obtained from fetching a document
 * using HttpFetchClient.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class HttpFetchClientResponse implements IHttpFetchResponse {

    @ToStringSummary
    private final List<Pair<IHttpFetchResponse, IHttpFetcher>> responses =
            new ArrayList<>();

    public HttpFetchClientResponse() {
        super();
    }

    public void addResponse(IHttpFetchResponse resp, IHttpFetcher fetcher) {
        this.responses.add(0, new ImmutablePair<>(resp, fetcher));
    }

    @Override
    public CrawlState getCrawlState() {
        return lastResponse().map(
                IHttpFetchResponse::getCrawlState).orElse(null);
    }
    @Override
    public int getStatusCode() {
        return lastResponse().map(IHttpFetchResponse::getStatusCode).orElse(0);
    }
    @Override
    public String getReasonPhrase() {
        return lastResponse().map(
                IHttpFetchResponse::getReasonPhrase).orElse(null);
    }
    @Override
    public String getUserAgent() {
        return lastResponse().map(
                IHttpFetchResponse::getUserAgent).orElse(null);
    }
    @Override
    public Exception getException() {
        return lastResponse().map(
                IHttpFetchResponse::getException).orElse(null);
    }
//    @Override
//    public List<String> getRedirectTrail() {
//        return lastResponse().map(IHttpFetchResponse::getRedirectTrail).orElse(
//                Collections.emptyList());
//    }
    @Override
    public String getRedirectTarget() {
        return lastResponse().map(
                IHttpFetchResponse::getRedirectTarget).orElse(null);
    }

    public List<IHttpFetchResponse> getResponses() {
        return responses.stream().map(
                Pair::getLeft).collect(Collectors.toList());
    }

    private Optional<IHttpFetchResponse> lastResponse() {
        if (responses.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(responses.get(0).getLeft());
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        if (responses.isEmpty()) {
            return "[No HTTP fetch responses.]";
        }
        // Print last response
        ReflectionToStringBuilder b = new ReflectionToStringBuilder(
                lastResponse().get(), ToStringStyle.NO_CLASS_NAME_STYLE);
        b.setExcludeNullValues(true);
        String str = b.toString();
        if (responses.size() > 1) {
            str += " (+ " + (responses.size() - 1)
                    + " other invalid responses)";
        }
        return str;
    }
}
