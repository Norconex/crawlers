/* Copyright 2023-2024 Norconex Inc.
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

import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.mocks.MockFetchRequest;
import com.norconex.crawler.core.mocks.MockFetchResponse;
import com.norconex.crawler.core.mocks.MockFetchResponseImpl;
import com.norconex.crawler.core.mocks.MockFetcher;

class MultiFetcherTest {

    @Test
    void testAcceptedAndOKResponse() {
        var mf = multiFetcher(
                new MockFetcher()
                        .setDenyRequest(false)
                        .setReturnBadStatus(false)
        );
        var resp = mf.fetch(new MockFetchRequest("someRef"));
        assertThat(
                ((MultiFetchResponse<?>) resp)
                        .getFetchResponses()
        ).hasSize(1);
    }

    @Test
    void testAcceptedAndBadResponse() {
        var mf = multiFetcher(
                new MockFetcher()
                        .setDenyRequest(false)
                        .setReturnBadStatus(true)
        );
        var resp = mf.fetch(new MockFetchRequest("someRef"));
        assertThat(
                ((MultiFetchResponse<?>) resp)
                        .getFetchResponses()
        ).hasSize(2);
    }

    @Test
    void testDenied() {
        var mf = multiFetcher(
                new MockFetcher()
                        .setDenyRequest(true)
                        .setReturnBadStatus(false)
        ); // <-- irrelevant
        var resp = mf.fetch(new MockFetchRequest("someRef"));
        // Even though there are no matching fetchers, there is always
        // at least once response returned. In this case, it will be
        // one with status UNSUPPORTED.
        assertThat(
                ((MultiFetchResponse<?>) resp)
                        .getFetchResponses()
        ).hasSize(1);
        assertThat(resp.getCrawlDocState()).isSameAs(CrawlDocState.UNSUPPORTED);
    }

    private MultiFetcher<MockFetchRequest, MockFetchResponse> multiFetcher(
            MockFetcher... fetchers
    ) {
        return MultiFetcher
                .<MockFetchRequest, MockFetchResponse>builder()
                .fetchers(List.of(fetchers))
                .responseListAdapter(MockMultiFetcherResponse::new)
                .unsuccessfulResponseAdaptor(
                        (state, msg, ex) -> new MockFetchResponseImpl()
                                .setCrawlDocState(state)
                                .setReasonPhrase(msg)
                                .setException(ex)
                )
                .maxRetries(1)
                .retryDelay(Duration.ofMillis(2))
                .build();
    }

    static class MockMultiFetcherResponse
            extends MultiFetchResponse<MockFetchResponse>
            implements MockFetchResponse {
        public MockMultiFetcherResponse(
                List<MockFetchResponse> fetchResponses
        ) {
            super(fetchResponses);
        }
    }
}
