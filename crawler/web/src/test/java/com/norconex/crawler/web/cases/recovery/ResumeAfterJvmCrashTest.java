/* Copyright 2019-2025 Norconex Inc.
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
package com.norconex.crawler.web.cases.recovery;

import static com.norconex.crawler.web.mocks.MockWebsite.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.mocks.MockWebsite;
import com.norconex.grid.local.LocalGridConnector;

import lombok.extern.slf4j.Slf4j;

/**
 * Test that the right amount of docs are crawled after crashing and
 * and restarting the crawler.
 */
@MockServerSettings
@Slf4j
class ResumeAfterJvmCrashTest {

    //TODO also test with other grids? Or move to default core tests?
    @WebCrawlTest(gridConnectors = LocalGridConnector.class)
    void testResumeAfterJvmCrash(
            ClientAndServer client, WebCrawlerConfig cfg) throws IOException {
        var path = "/resumeAfterJvmCrash";

        MockWebsite.whenInfiniteDepth(client);

        var crasher = new JVMCrasher();

        cfg.setStartReferences(List.of(serverUrl(client, path + "/0000")));
        cfg.setNumThreads(1);
        cfg.setMaxDepth(-1);
        cfg.setMaxDocuments(10);
        cfg.setMetadataChecksummer(null);
        cfg.setDocumentChecksummer(null);

        ((LocalGridConnector) cfg.getGridConnector()).getConfiguration()
                .setAutoCommitBufferSize(1L)
                .setAutoCommitDelay(1L);

        // First run should crash with 6 commits only (0-5)
        cfg.addEventListener(crasher);
        var outcome = ExternalCrawlSessionLauncher.start(cfg);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());

        assertThat(outcome.getReturnValue()).isNotZero();
        assertThat(outcome.getCommitterAfterLaunch()
                .getUpsertCount()).isEqualTo(6);
        assertThat(WebTestUtil.lastSortedRequestReference(
                outcome.getCommitterAfterLaunch())).endsWith(path + "/0005");

        // Second run, it should resume and finish normally, crawling
        // an additional 10 max docs, totaling 16.
        cfg.removeEventListener(crasher);
        outcome = ExternalCrawlSessionLauncher.start(cfg);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(outcome.getCommitterAfterLaunch()
                .getUpsertCount()).isEqualTo(10);
        assertThat(outcome.getCommitterCombininedLaunches()
                .getUpsertCount()).isEqualTo(16);
        assertThat(WebTestUtil.lastSortedRequestReference(
                outcome.getCommitterAfterLaunch())).endsWith(path + "/0015");

        // Recrawl fresh without crash. Since we do not check for duplicates,
        // it should find 10 "new", added to previous 10.
        outcome = ExternalCrawlSessionLauncher.start(cfg);
        //        Sleeper.sleepMillis(100); // Give enough time for files to be written
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(outcome.getCommitterAfterLaunch()
                .getUpsertCount()).isEqualTo(10);
        assertThat(outcome.getCommitterCombininedLaunches()
                .getUpsertCount()).isEqualTo(26);
        assertThat(WebTestUtil.lastSortedRequestReference(
                outcome.getCommitterAfterLaunch())).endsWith(path + "/0025");
    }
}
