/* Copyright 2021 Norconex Inc.
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

import java.util.List;

import com.norconex.collector.core.crawler.event.impl.DeleteRejectedEventListener;
import com.norconex.collector.core.crawler.event.impl.StopCrawlerOnMaxEventListener;
import com.norconex.collector.core.crawler.event.impl.StopCrawlerOnMaxEventListener.OnMultiple;
import com.norconex.collector.core.filter.impl.ReferenceFilter;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractInfiniteDepthTestFeature;
import com.norconex.committer.core3.DeleteRequest;
import com.norconex.committer.core3.UpsertRequest;
import com.norconex.committer.core3.impl.MemoryCommitter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * Test the stopping of a crawler upon reaching configured maximum number of
 * event.
 * {@link DeleteRejectedEventListener}.
 * @author Pascal Essiembre
 */
public class StopCrawlerOnMaxEvent extends AbstractInfiniteDepthTestFeature {

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {
        StopCrawlerOnMaxEventListener lis = new StopCrawlerOnMaxEventListener();
        lis.setEventMatcher(TextMatcher.csv(
                "DOCUMENT_COMMITTED_UPSERT,REJECTED_FILTER"));
        lis.setMaximum(10);
        lis.setOnMultiple(OnMultiple.SUM);
        cfg.addEventListeners(lis);
        cfg.setNumThreads(1);
        cfg.setMaxDocuments(-1);

        // reject references with odd depth number
        cfg.setDocumentFilters(new ReferenceFilter(
                TextMatcher.regex(".*[13579]$"), OnMatch.EXCLUDE));
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        // Expected: 3 upserts, 0 deletes
        List<UpsertRequest> upserts = committer.getUpsertRequests();
        List<DeleteRequest> deletes = committer.getDeleteRequests();

        assertListSize("upserts", upserts, 6);
        assertListSize("deletes", deletes, 0);
    }
}
