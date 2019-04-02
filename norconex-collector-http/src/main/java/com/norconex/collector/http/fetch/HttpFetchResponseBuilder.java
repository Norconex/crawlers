/* Copyright 2019 Norconex Inc.
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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.collector.core.data.CrawlState;

/**
 * Builder facilitating creation of an HTTP fetch response.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class HttpFetchResponseBuilder {

    private final HttpFetchResponseImpl response = new HttpFetchResponseImpl();

    public HttpFetchResponseBuilder() {
        super();
    }
    public HttpFetchResponseBuilder(IHttpFetchResponse r) {
        super();
        response.crawlState = r.getCrawlState();
        response.reasonPhrase = r.getReasonPhrase();
        response.statusCode = r.getStatusCode();
        response.userAgent = r.getUserAgent();
    }

    public HttpFetchResponseBuilder setUserAgent(String userAgent) {
        response.userAgent = userAgent;
        return this;
    }
    public HttpFetchResponseBuilder setCrawlState(CrawlState crawlState) {
        response.crawlState = crawlState;
        return this;
    }
    public HttpFetchResponseBuilder setStatusCode(int statusCode) {
        response.statusCode = statusCode;
        return this;
    }
    public HttpFetchResponseBuilder setReasonPhrase(String reasonPhrase) {
        response.reasonPhrase = reasonPhrase;
        return this;
    }
    public IHttpFetchResponse build() {
        if (response.crawlState == null) {
            throw new IllegalArgumentException("Crawl state cannot be null.");
        }
        return response;
    }

    public static HttpFetchResponseBuilder unsupported() {
        return new HttpFetchResponseBuilder()
            .setCrawlState(CrawlState.UNSUPPORTED)
            .setReasonPhrase("HTTP Fetch operation unsupported by fetcher.")
            .setStatusCode(-1);
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
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }


    private class HttpFetchResponseImpl implements IHttpFetchResponse {
        private CrawlState crawlState;
        private int statusCode;
        private String reasonPhrase;
        private String userAgent;
        @Override
        public CrawlState getCrawlState() {
            return crawlState;
        }
        @Override
        public int getStatusCode() {
            return statusCode;
        }
        @Override
        public String getReasonPhrase() {
            return reasonPhrase;
        }
        @Override
        public String getUserAgent() {
            return userAgent;
        }
    }
}
