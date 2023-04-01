/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.web.session.recovery;

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.WebsiteMock;

import lombok.extern.slf4j.Slf4j;

/**
 * Test that the right amount of docs are crawled after stoping
 * and starting the collector.
 */
@MockServerSettings
@Slf4j
class ResumeAfterStopTest {

    @Test
    void testResumeAfterStop(ClientAndServer client, @TempDir Path tempDir) {
        var path = "/resumeAfterStop";

        WebsiteMock.whenInfinitDepth(client);

        var stopper = new CrawlSessionStopper();

        var crawlSessionConfig = TestWebCrawlSession
                .forStartReferences(serverUrl(client, path + "/0000"))
                .crawlSessionSetup(cfg -> cfg.setWorkDir(tempDir))
                .crawlerSetup(cfg -> {
                    cfg.setNumThreads(1);
                    cfg.setMaxDepth(-1);
                    cfg.setMaxDocuments(10);
                    cfg.setMetadataChecksummer(null);
                    cfg.setDocumentChecksummer(null);
                })
                .crawlSessionConfig();

        // First run should stop with 7 commits only (0-6)
        crawlSessionConfig.addEventListener(stopper);
        var outcome = ExternalCrawlSessionLauncher.start(crawlSessionConfig);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(outcome.getCommitterAfterLaunch()
                .getUpsertCount()).isEqualTo(7);
        assertThat(WebTestUtil.lastSortedRequestReference(
                outcome.getCommitterAfterLaunch())).isEqualTo(
                        WebsiteMock.serverUrl(client, path + "/0006"));

        // Second run, it should resume and finish normally, crawling
        // 10 docs in this session.
        crawlSessionConfig.removeEventListener(stopper);
        outcome = ExternalCrawlSessionLauncher.start(crawlSessionConfig);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(outcome.getCommitterAfterLaunch()
                .getUpsertCount()).isEqualTo(10);
        assertThat(outcome.getCommitterCombininedLaunches()
                .getUpsertCount()).isEqualTo(17);
        assertThat(WebTestUtil.lastSortedRequestReference(
                outcome.getCommitterAfterLaunch())).isEqualTo(
                        WebsiteMock.serverUrl(client, path + "/0016"));

        // Recrawl fresh without crash. Since we do not check for duplicates,
        // it should find 10 "new", added to previous 10.
        outcome = ExternalCrawlSessionLauncher.start(crawlSessionConfig);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(outcome.getCommitterAfterLaunch()
                .getUpsertCount()).isEqualTo(10);
        assertThat(outcome.getCommitterCombininedLaunches()
                .getUpsertCount()).isEqualTo(27);
        assertThat(WebTestUtil.lastSortedRequestReference(
                outcome.getCommitterAfterLaunch())).isEqualTo(
                        WebsiteMock.serverUrl(client, path + "/0026"));
    }
}
