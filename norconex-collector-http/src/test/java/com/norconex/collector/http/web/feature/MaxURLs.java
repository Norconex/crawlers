/* Copyright 2019 Norconex Inc.
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
package com.norconex.collector.http.web.feature;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractInfiniteDepthTestFeature;
import com.norconex.committer.core3.impl.MemoryCommitter;

/**
 * Test that MaxURLs setting is respected.
 * @author Pascal Essiembre
 */
public class MaxURLs extends AbstractInfiniteDepthTestFeature {

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig crawlerConfig)
            throws Exception {
        crawlerConfig.setMaxDocuments(15);
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        assertListSize("URLs", committer.getUpsertRequests(), 15);
    }
}
