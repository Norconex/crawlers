/* Copyright 2019-2024 Norconex Inc.
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

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.store.impl.mvstore.MvStoreDataStoreEngine2;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.crawler.web.stubs.CrawlerConfigStubs;

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

        WebsiteMock.whenInfiniteDepth(client);

        var crasher = new JVMCrasher();

        var cfg = CrawlerConfigStubs.memoryCrawlerConfig(tempDir);
        cfg.setStartReferences(List.of(serverUrl(client, path + "/0000")));
        cfg.setWorkDir(tempDir);
        var storeCfg = ((MvStoreDataStoreEngine2) cfg.getDataStoreEngine())
                .getConfiguration();
        storeCfg.setAutoCommitDelay(1L);
        cfg.setNumThreads(1);
        cfg.setMaxDepth(-1);
        cfg.setMaxDocuments(10);
        cfg.setMetadataChecksummer(null);
        cfg.setDocumentChecksummer(null);

        // First run should crash with 6 commits only (0-5)
        cfg.addEventListener(crasher);
        var outcome = ExternalCrawlSessionLauncher.start(cfg);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isNotZero();
        assertThat(
                outcome.getCommitterAfterLaunch()
                        .getUpsertCount()).isEqualTo(6);
        assertThat(
                WebTestUtil.lastSortedRequestReference(
                        outcome.getCommitterAfterLaunch())).isEqualTo(
                                WebsiteMock.serverUrl(client, path + "/0005"));

        // Second run, it should resume and finish normally, crawling
        // an additional 10 max docs, totaling 16.
        cfg.removeEventListener(crasher);
        outcome = ExternalCrawlSessionLauncher.start(cfg);
        Sleeper.sleepMillis(500); // Give enough time for files to be written
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(
                outcome.getCommitterAfterLaunch()
                        .getUpsertCount()).isEqualTo(10);
        assertThat(
                outcome.getCommitterCombininedLaunches()
                        .getUpsertCount()).isEqualTo(16);
        //Due to thread synching and timing delays, allow one-off.
        assertThat(
                EqualsUtil.equalsAny(
                        WebTestUtil.lastSortedRequestReference(
                                outcome.getCommitterAfterLaunch()),
                        WebsiteMock.serverUrl(client, path + "/0014"),
                        WebsiteMock.serverUrl(client, path + "/0015")))
                                .isTrue();

        // Recrawl fresh without crash. Since we do not check for duplicates,
        // it should find 10 "new", added to previous 10.
        outcome = ExternalCrawlSessionLauncher.start(cfg);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(
                outcome.getCommitterAfterLaunch()
                        .getUpsertCount()).isEqualTo(10);
        assertThat(
                outcome.getCommitterCombininedLaunches()
                        .getUpsertCount()).isEqualTo(26);
        //Due to thread synching and timing delays, allow one-off.
        assertThat(
                EqualsUtil.equalsAny(
                        WebTestUtil.lastSortedRequestReference(
                                outcome.getCommitterAfterLaunch()),
                        WebsiteMock.serverUrl(client, path + "/0024"),
                        WebsiteMock.serverUrl(client, path + "/0025")))
                                .isTrue();
    }
}
