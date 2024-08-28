/* Copyright 2024 Norconex Inc.
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
package com.norconex.committer.azurecognitivesearch;

import static com.norconex.committer.azurecognitivesearch.AzureSearchCommitterTest.createAzureSearchCommitter;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.map.Properties;

class AzureSearchClientTest {

    @Test
    void testInvalidDocKeyMustThrow() {
        assertThatExceptionOfType(CommitterException.class).isThrownBy(() -> {
            var committer = createAzureSearchCommitter(1234);
            var cfg = committer.getConfiguration();
            var client = new AzureSearchClient(cfg);
            client.post(List.of(mockRequestWithBadKey()).iterator());
        });
    }

    @Test
    void testInvalidDocKeyIgnoreErrors() {
        assertThatNoException().isThrownBy(() -> {
            var committer = createAzureSearchCommitter(1234);
            var cfg = committer.getConfiguration();
            cfg.setIgnoreResponseErrors(true);
            cfg.setIgnoreValidationErrors(true);
            var client = new AzureSearchClient(cfg);
            client.post(List.of(mockRequestWithBadKey()).iterator());
        });
    }

    @Test
    void testPostExceptionsWrapped() {
        assertThatExceptionOfType(CommitterException.class).isThrownBy(() -> {
            var committer = createAzureSearchCommitter(1234);
            var cfg = committer.getConfiguration();
            var client = new AzureSearchClient(cfg);
            client.post(new Iterator<CommitterRequest>() {
                @Override
                public CommitterRequest next() {
                    throw new UnsupportedOperationException("Fake exception");
                }
                @Override
                public boolean hasNext() {
                    return true;
                }
            });
        });
    }

    @Test
    void testUnsupportedRequestMustThrow() {
        assertThatExceptionOfType(CommitterException.class).isThrownBy(() -> {
            var committer = createAzureSearchCommitter(1234);
            var cfg = committer.getConfiguration();
            var client = new AzureSearchClient(cfg);
            var req = new CommitterRequest() {
                @Override
                public String getReference() {
                    return "BLAH";
                }
                @Override
                public Properties getMetadata() {
                    return new Properties();
                }
            };
            client.post(List.<CommitterRequest>of(req).iterator());
        });
    }

    @Test
    void testHandleResponse() {
        assertThatNoException().isThrownBy(() -> {
        var committer = createAzureSearchCommitter(1234);
        var cfg = committer.getConfiguration();
        cfg.setIgnoreResponseErrors(true);
        var client = new AzureSearchClient(cfg);
        client.handleResponse(new BasicClassicHttpResponse(
                500, "-External- Server Error."));
        });
    }

    private static CommitterRequest mockRequestWithBadKey() {
        return new UpsertRequest(
                "_BLAH",
                new Properties(),
                IOUtils.toInputStream("blah", UTF_8));
    }
}
