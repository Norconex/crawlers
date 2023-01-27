/* Copyright 2023 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class GenericMultiFetcherTest {

    @Test
    void testGenericMultiFetcher() {
        var mf = GenericMultiFetcher
                .<MockFetchRequest, MockFetchResponse>builder()
                .fetchers(List.of(new MockFetcher().setDenyRequest(false)))
                .multiResponseAdaptor(resps -> {
                    var gmfr = new MockFetchResponse();
                    resps.forEach((k, v) ->
                    gmfr.addFetchResponse(k, v));
                    return gmfr;
                })
                .unsuccessfulResponseAdaptor((state, msg, ex) -> null)
                .maxRetries(1)
                .retryDelay(2)
                .build();

        var resp = mf.fetch(new MockFetchRequest("someRef"));

        assertThat(resp.getFetchResponses()).hasSize(1);
    }

    @Test
    void testNoneAccepting() {
        var mf = GenericMultiFetcher
                .<MockFetchRequest, MockFetchResponse>builder()
                .fetchers(List.of(new MockFetcher().setDenyRequest(true)))
                .multiResponseAdaptor(resps -> {
                    var gmfr = new MockFetchResponse();
                    resps.forEach((k, v) ->
                    gmfr.addFetchResponse(k, v));
                    return gmfr;
                })
                .unsuccessfulResponseAdaptor(
                        (state, msg, ex) ->  new MockFetchResponse())
                .maxRetries(1)
                .retryDelay(2)
                .build();

        var resp = mf.fetch(new MockFetchRequest("someRef"));
        assertThat(resp.getFetchResponses()).hasSize(1);
    }

}
