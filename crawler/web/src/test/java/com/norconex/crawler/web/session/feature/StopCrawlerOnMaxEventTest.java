/* Copyright 2021-2023 Norconex Inc.
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
package com.norconex.crawler.web.session.feature;

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.committer.core.CommitterEvent;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.crawler.event.impl.DeleteRejectedEventListener;
import com.norconex.crawler.core.crawler.event.impl.StopCrawlerOnMaxEventListener;
import com.norconex.crawler.core.crawler.event.impl.StopCrawlerOnMaxEventListener.OnMultiple;
import com.norconex.crawler.core.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * Test the stopping of a crawler upon reaching configured maximum number of
 * event.
 * {@link DeleteRejectedEventListener}.
 */
@MockServerSettings
class StopCrawlerOnMaxEventTest {

    @Test
    void testStopCrawlerOnMaxEvent(ClientAndServer client) {
        WebsiteMock.whenInfinitDepth(client);

        var mem = TestWebCrawlSession
            .forStartReferences(serverUrl(client, "/stopCrawlerOnMaxEvent"))
            .crawlerSetup(cfg -> {
                var lis = new StopCrawlerOnMaxEventListener();
                lis.setEventMatcher(TextMatcher.csv(
                        CommitterEvent.COMMITTER_UPSERT_END
                        + "," + CrawlerEvent.REJECTED_FILTER));
                lis.setMaximum(10);
                lis.setOnMultiple(OnMultiple.SUM);
                cfg.addEventListeners(List.of(lis));
                cfg.setNumThreads(1);
                cfg.setMaxDocuments(-1);

                // reject references with odd depth number
                cfg.setDocumentFilters(List.of(new GenericReferenceFilter(
                        TextMatcher.regex(".*[13579]$"), OnMatch.EXCLUDE)));
            })
            .crawl();

        // Expected: 6 upserts, 0 deletes
        assertThat(mem.getUpsertCount()).isEqualTo(6);
        assertThat(mem.getDeleteCount()).isZero();
    }
}
