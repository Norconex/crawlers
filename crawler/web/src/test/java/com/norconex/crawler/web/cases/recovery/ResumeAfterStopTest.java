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

import static com.norconex.crawler.web.mocks.MockWebsite.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.web.WebCrawler;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.mocks.MockWebsite;
import com.norconex.crawler.web.stubs.CrawlerConfigStubs;

import lombok.extern.slf4j.Slf4j;

/**
 * Test that the right amount of docs are crawled after stoping
 * and starting the collector.
 */
@MockServerSettings
@Slf4j
class ResumeAfterStopTest {

    private static final int MAX_DOCS = 25;

    @Test
    void testResumeAfterStop(ClientAndServer client, @TempDir Path tempDir)
            throws IOException, InterruptedException, ExecutionException,
            TimeoutException {
        var path = "/resumeAfterStop";

        MockWebsite.whenInfiniteDepth(client);

        //--- Configure ---
        var crawlerDir = tempDir.resolve("crawler");
        var cfg = CrawlerConfigStubs.memoryCrawlerConfig(crawlerDir);
        cfg.setStartReferences(List.of(serverUrl(client, path + "/0000")));
        cfg.setWorkDir(crawlerDir);
        cfg.setNumThreads(1);
        cfg.setMaxDepth(-1);
        cfg.setMaxDocuments(MAX_DOCS);
        cfg.setDelayResolver(WebTestUtil.delayResolver(1000));
        cfg.setMetadataChecksummer(null);
        cfg.setDocumentChecksummer(null);
        WebTestUtil.addTestCommitterOnce(cfg);

        var executor = Executors.newFixedThreadPool(2);

        //--- Launch ---
        LOG.debug("Launching crawler in its own thread...");
        var futureCrawlOutcome =
                executor.submit(() -> ExternalCrawlSessionLauncher.start(cfg));
        LOG.debug("Crawler launched...");

        // wait until a few docs (3) are crawled
        while (WebTestUtil.getTestCommitter(cfg) == null
                || Files.list(WebTestUtil.getTestCommitter(cfg).getDir())
                        .count() < 2) {
            Sleeper.sleepMillis(500);
        }

        //--- Stop ---
        LOG.debug("Launching stop command...");
        // Doing it in the main thread since running it externally like
        // above is synchronized.
        WebCrawler.create(cfg).stop();
        LOG.debug("Stop command launched...");

        var crawlOutcome = futureCrawlOutcome.get(20, TimeUnit.SECONDS);
        var docCount = crawlOutcome.getCommitterAfterLaunch().getRequestCount();
        assertThat(docCount).isBetween(3, MAX_DOCS - 2);

        //--- Resume ---
        var finalCountExpecation = MAX_DOCS + docCount;

        cfg.setDelayResolver(WebTestUtil.delayResolver(0));
        var finalOutcome = ExternalCrawlSessionLauncher.start(cfg);
        LOG.debug(finalOutcome.getStdErr());
        LOG.debug(finalOutcome.getStdOut());
        assertThat(finalOutcome.getReturnValue()).isZero();
        assertThat(finalOutcome.getCommitterAfterLaunch()
                .getUpsertCount()).isEqualTo(MAX_DOCS);
        assertThat(finalOutcome.getCommitterCombininedLaunches()
                .getUpsertCount()).isEqualTo(finalCountExpecation);
        assertThat(WebTestUtil.lastSortedRequestReference(
                finalOutcome.getCommitterAfterLaunch())).isEqualTo(
                        MockWebsite.serverUrl(client,
                                path + "/00" + (finalCountExpecation - 1)));

    }
}
