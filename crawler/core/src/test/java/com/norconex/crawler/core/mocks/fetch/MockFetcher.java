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

import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.fetch.AbstractFetcher;
import com.norconex.crawler.core.fetch.BaseFetcherConfig;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.FetchRequest;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MockFetcher extends AbstractFetcher<BaseFetcherConfig> {

    private BaseFetcherConfig configuration = new BaseFetcherConfig();

    private Boolean denyRequest;
    private boolean returnBadStatus;
    private boolean randomDocContent;

    @Override
    public MockFetchResponse fetch(FetchRequest fetchRequest)
            throws FetchException {
        var req = (MockFetchRequest) fetchRequest;
        var resp = new MockFetchResponseImpl();
        resp.setResolutionStatus(
                returnBadStatus ? CrawlDocStatus.BAD_STATUS
                        : CrawlDocStatus.NEW);
        var content = randomDocContent
                ? "Fake content for: " + req.getRef()
                        + "\nRandomness: " + TimeIdGenerator.next()
                : "Fake content for: " + req.getRef();
        fetchRequest.getDoc().setInputStream(
                new ByteArrayInputStream(content.getBytes()));
        return resp;
    }

    @Override
    public boolean acceptRequest(@NonNull FetchRequest fetchRequest) {
        if (denyRequest == null) {
            return super.acceptRequest(fetchRequest);
        }
        return !denyRequest;
    }
}
