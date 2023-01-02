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
package com.norconex.crawler.core;

import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.IFetchRequest;
import com.norconex.crawler.core.fetch.IFetchResponse;
import com.norconex.crawler.core.fetch.IFetcher;

public class MockFetcher implements IFetcher<IFetchRequest, IFetchResponse> {

    @Override
    public boolean accept(IFetchRequest fetchRequest) {
        // TODO Auto-generated method stub
        return false;
    }
    @Override
    public IFetchResponse fetch(IFetchRequest fetchRequest)
            throws FetchException {
        // TODO Auto-generated method stub
        return null;
    }

}
