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

import com.norconex.collector.core.checksum.impl.MD5DocumentChecksummer;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractInfiniteDepthTestFeature;
import com.norconex.committer.core3.impl.MemoryCommitter;
import com.norconex.commons.lang.text.TextMatcher;

/**
 * Tests that the page does not produce any Committer addition on subsequent
 * run. Tests the page 4 times.  Only once should it find it as new.
 * Other attempts should be unmodified.
 * @author Pascal Essiembre
 */
//Test for https://github.com/Norconex/collector-http/issues/544
public class UnmodifiedMeta extends AbstractInfiniteDepthTestFeature {

    @Override
    public int numberOfRun() {
        return 4;
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {
        cfg.setMaxDepth(0);
        MD5DocumentChecksummer cs = new MD5DocumentChecksummer();
        cs.setFieldMatcher(TextMatcher.basic("article:modified_time"));
        cfg.setDocumentChecksummer(cs);
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        // Only first attempt should have 1 doc
        if (isFirstRun()) {
            assertListSize("document", committer.getUpsertRequests(), 1);
        } else {
            assertListSize("document", committer.getUpsertRequests(), 0);
        }
    }
}
