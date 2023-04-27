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

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.store.impl.mvstore.MVStoreDataStoreEngine;
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
class ResumeAfterJvmCrashTest {

    @Test
    void testResumeAfterJvmCrash(
            ClientAndServer client, @TempDir Path tempDir) {
        var path = "/resumeAfterJvmCrash";

        WebsiteMock.whenInfinitDepth(client);

        var crasher = new JVMCrasher();

        var crawlSessionConfig = TestWebCrawlSession
                .forStartReferences(serverUrl(client, path + "/0000"))
                .crawlSessionSetup(cfg -> cfg.setWorkDir(tempDir))
                .crawlerSetup(cfg -> {
                    cfg.setNumThreads(1);
                    cfg.setMaxDepth(-1);
                    cfg.setMaxDocuments(10);
                    cfg.setMetadataChecksummer(null);
                    cfg.setDocumentChecksummer(null);
                    var storeCfg = ((MVStoreDataStoreEngine)
                            cfg.getDataStoreEngine()).getConfiguration();
                    storeCfg.setAutoCommitDelay(1L);
                })
                .crawlSessionConfig();
        // First run should crash with 6 commits only (0-5)
        crawlSessionConfig.addEventListener(crasher);
        var outcome = ExternalCrawlSessionLauncher.start(crawlSessionConfig);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isEqualTo(13);
        assertThat(outcome.getCommitterAfterLaunch()
                .getUpsertCount()).isEqualTo(6);
        assertThat(WebTestUtil.lastSortedRequestReference(
                outcome.getCommitterAfterLaunch())).isEqualTo(
                        WebsiteMock.serverUrl(client, path + "/0005"));

        // Second run, it should resume and finish normally, crawling
        // an additional 10 max docs, totaling 16.
        crawlSessionConfig.removeEventListener(crasher);
        outcome = ExternalCrawlSessionLauncher.start(crawlSessionConfig);
        Sleeper.sleepMillis(500);  // Give enough time for files to be written
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(outcome.getCommitterAfterLaunch()
                .getUpsertCount()).isEqualTo(10);
        assertThat(outcome.getCommitterCombininedLaunches()
                .getUpsertCount()).isEqualTo(16);
        //Due to thread synching and timing delays, allow one-off.
        assertThat(EqualsUtil.equalsAny(
                WebTestUtil.lastSortedRequestReference(
                outcome.getCommitterAfterLaunch()),
                        WebsiteMock.serverUrl(client, path + "/0014"),
                        WebsiteMock.serverUrl(client, path + "/0015")))
            .isTrue();

        // Recrawl fresh without crash. Since we do not check for duplicates,
        // it should find 10 "new", added to previous 10.
        outcome = ExternalCrawlSessionLauncher.start(crawlSessionConfig);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(outcome.getCommitterAfterLaunch()
                .getUpsertCount()).isEqualTo(10);
        assertThat(outcome.getCommitterCombininedLaunches()
                .getUpsertCount()).isEqualTo(26);
        assertThat(WebTestUtil.lastSortedRequestReference(
                outcome.getCommitterAfterLaunch())).isEqualTo(
                        WebsiteMock.serverUrl(client, path + "/0025"));
    }
}
