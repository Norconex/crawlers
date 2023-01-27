/* Copyright 2022 Norconex Inc.
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

import com.norconex.commons.lang.xml.XML;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MockFetcher
    extends AbstractFetcher<MockFetchRequest, MockFetchResponse> {

    private Boolean denyRequest;

    @Override
    public MockFetchResponse fetch(MockFetchRequest fetchRequest)
            throws FetchException {
        // TODO Auto-generated method stub
        return new MockFetchResponse();
    }

    @Override
    protected boolean acceptRequest(@NonNull MockFetchRequest fetchRequest) {
        if (denyRequest == null) {
            return super.acceptRequest(fetchRequest);
        }
        return !denyRequest;
    }

    @Override
    protected void loadFetcherFromXML(XML xml) {
        // NOOP
    }

    @Override
    protected void saveFetcherToXML(XML xml) {
        // NOOP
    }
}
