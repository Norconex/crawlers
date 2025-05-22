/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.crawler.web.fetch;

import java.util.List;

import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.AggregatedFetchResponse;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * HTTP Multi-response information obtained from fetching a document.
 * Getter methods return values from the last response.
 * @since 3.0.0
 */
@EqualsAndHashCode
@ToString
public class AggregatedWebFetchResponse
        extends AggregatedFetchResponse
        implements WebFetchResponse {

    public AggregatedWebFetchResponse(List<FetchResponse> fetchResponses) {
        super(fetchResponses);
    }

    @Override
    public String getRedirectTarget() {
        return getLastFetchResponse()
                .map(WebFetchResponse.class::cast)
                .map(WebFetchResponse::getRedirectTarget)
                .orElse(null);
    }

    @Override
    public String getUserAgent() {
        return getLastFetchResponse()
                .map(WebFetchResponse.class::cast)
                .map(WebFetchResponse::getUserAgent)
                .orElse(null);
    }
}
