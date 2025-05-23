/* Copyright 2023-2025 Norconex Inc.
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

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.mocks.fetch.MockFetchRequest;
import com.norconex.crawler.core.mocks.fetch.MockFetchResponse;
import com.norconex.crawler.core.mocks.fetch.MockFetchResponseImpl;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;

class MultiFetcherTest {

    @Test
    void testAcceptedAndOKResponse() {
        var mf = multiFetcher(new MockFetcher()
                .setDenyRequest(false)
                .setReturnBadStatus(false));
        var resp = mf.fetch(new MockFetchRequest("someRef"));
        assertThat(((AggregatedFetchResponse) resp)
                .getFetchResponses()).hasSize(1);
    }

    @Test
    void testAcceptedAndBadResponse() {
        var mf = multiFetcher(new MockFetcher()
                .setDenyRequest(false)
                .setReturnBadStatus(true));
        var resp = mf.fetch(new MockFetchRequest("someRef"));
        assertThat(((AggregatedFetchResponse) resp).getFetchResponses())
                .hasSize(2);
    }

    @Test
    void testDenied() {
        var mf = multiFetcher(new MockFetcher()
                .setDenyRequest(true)
                .setReturnBadStatus(false)); // <-- irrelevant
        var resp = mf.fetch(new MockFetchRequest("someRef"));
        // Even though there are no matching fetchers, there is always
        // at least once response returned. In this case, it will be
        // one with status UNSUPPORTED.
        assertThat(((AggregatedFetchResponse) resp)
                .getFetchResponses()).hasSize(1);
        assertThat(resp.getResolutionStatus())
                .isSameAs(CrawlDocStatus.UNSUPPORTED);
    }

    private MultiFetcher multiFetcher(MockFetcher... fetchers) {
        return MultiFetcher
                .builder()
                .fetchers(List.of(fetchers))
                .responseAggregator(MockMultiFetcherResponse::new)
                .unsuccessfulResponseFactory(
                        (state, msg, ex) -> new MockFetchResponseImpl()
                                .setResolutionStatus(state)
                                .setReasonPhrase(msg)
                                .setException(ex))
                .maxRetries(1)
                .retryDelay(Duration.ofMillis(2))
                .build();
    }

    static class MockMultiFetcherResponse
            extends AggregatedFetchResponse implements MockFetchResponse {
        public MockMultiFetcherResponse(
                FetchRequest req, List<FetchResponse> fetchResponses) {
            super(fetchResponses);
        }
    }
}
