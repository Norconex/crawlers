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
package com.norconex.crawler.core.mocks.fetch;

import java.io.ByteArrayInputStream;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core.fetch.AbstractFetcher;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.ledger.ProcessingOutcome;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MockFetcher extends AbstractFetcher<MockFetcherConfig> {

    private MockFetcherConfig configuration = new MockFetcherConfig();

    @Override
    public MockFetchResponse fetch(FetchRequest fetchRequest)
            throws FetchException {
        if (configuration.getDelay() != null) {
            Sleeper.sleepMillis(configuration.getDelay().toMillis());
        }
        var ref = fetchRequest.getDoc().getReference();
        if (configuration.isThrowFetchException()
                || configuration.getThrowOnRefs().contains(ref)) {
            throw new FetchException(
                    "MockFetcher: forced exception for: " + ref);
        }
        var resp = new MockFetchResponseImpl();
        resp.setProcessingOutcome(
                configuration.isReturnBadStatus()
                        ? ProcessingOutcome.BAD_STATUS
                        : ProcessingOutcome.NEW);
        var content = configuration.isRandomDocContent()
                ? "Fake content for: " + ref
                        + "\nRandomness: " + TimeIdGenerator.next()
                : "Fake content for: " + ref;
        fetchRequest.getDoc().setInputStream(
                new ByteArrayInputStream(content.getBytes()));
        return resp;
    }

    @Override
    public boolean acceptRequest(@NonNull FetchRequest fetchRequest) {
        var ref = fetchRequest.getDoc().getReference();
        if (configuration.isThrowOnAccept()
                || configuration.getThrowOnAcceptRefs().contains(ref)) {
            throw new RuntimeException(
                    "MockFetcher: simulated pipeline exception for: " + ref);
        }
        if (configuration.getDenyRequest() == null) {
            return super.acceptRequest(fetchRequest);
        }
        return !configuration.getDenyRequest();
    }
}
