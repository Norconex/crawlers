/* Copyright 2021-2024 Norconex Inc.
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
package com.norconex.crawler.web.cases.feature;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.committer.core.CommitterEvent;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.commands.crawl.task.operations.filter.OnMatch;
import com.norconex.crawler.core.commands.crawl.task.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.event.listeners.DeleteRejectedEventListener;
import com.norconex.crawler.core.event.listeners.StopCrawlerOnMaxEventListener;
import com.norconex.crawler.core.event.listeners.StopCrawlerOnMaxEventListenerConfig.OnMultiple;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;

/**
 * Test the stopping of a crawler upon reaching configured maximum number of
 * event.
 * {@link DeleteRejectedEventListener}.
 */
@MockServerSettings
class StopCrawlerOnMaxEventTest {

    @WebCrawlTest
    void testStopCrawlerOnMaxEvent(
            ClientAndServer client, WebCrawlerConfig cfg) {

        WebsiteMock.whenInfiniteDepth(client);

        cfg.setStartReferences(List.of(
                WebsiteMock.serverUrl(client, "/stopCrawlerOnMaxEvent")));
        var lis = new StopCrawlerOnMaxEventListener();
        lis.getConfiguration().setEventMatcher(
                TextMatcher.csv(CommitterEvent.COMMITTER_UPSERT_END
                        + "," + CrawlerEvent.REJECTED_FILTER));
        lis.getConfiguration().setMaximum(10);
        lis.getConfiguration().setOnMultiple(OnMultiple.SUM);
        cfg.addEventListeners(List.of(lis));
        cfg.setNumThreads(1);
        cfg.setMaxDocuments(-1);

        // reject references with odd depth number
        cfg.setDocumentFilters(List.of(Configurable.configure(
                new GenericReferenceFilter(), c -> c
                        .setValueMatcher(TextMatcher.regex(".*[13579]$"))
                        .setOnMatch(OnMatch.EXCLUDE))));

        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        // Expected: 6 upserts, 0 deletes
        assertThat(mem.getUpsertCount()).isEqualTo(6);
        assertThat(mem.getDeleteCount()).isZero();
    }
}
