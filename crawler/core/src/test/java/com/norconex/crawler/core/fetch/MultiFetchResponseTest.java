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

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.doc.DocResolutionStatus;

import lombok.Data;

class MultiFetchResponseTest {

    @Data
    static class TestResponse implements FetchResponse {
        private final String salt;

        @Override
        public int getStatusCode() {
            return 123;
        }

        @Override
        public String getReasonPhrase() {
            return "Just because.";
        }

        @Override
        public Exception getException() {
            return new IllegalArgumentException("TEST");
        }

        @Override
        public DocResolutionStatus getResolutionStatus() {
            return DocResolutionStatus.MODIFIED;
        }
    }

    @Test
    void testGenericMultiFetchResponse() {
        var resp1 = new TestResponse("resp1");
        var resp2 = new TestResponse("resp2");

        var gmfr = new MultiFetchResponse<>(List.of(resp1, resp2));

        assertThat(gmfr.getStatusCode()).isEqualTo(123);
        assertThat(gmfr.getReasonPhrase()).isEqualTo("Just because.");
        assertThat(gmfr.getException().getMessage()).isEqualTo("TEST");
        assertThat(gmfr.getResolutionStatus())
                .isSameAs(DocResolutionStatus.MODIFIED);
        assertThat(gmfr.getFetchResponses()).containsExactlyInAnyOrder(
                resp1, resp2);
        assertThat(gmfr.getLastFetchResponse()).containsSame(resp2);
        assertThat(gmfr).hasToString("123 Just because.");
    }
}
