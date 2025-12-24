/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.test;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;

class StandaloneHarnessImpl implements CrawlTestHarness {

    private final CrawlConfig cfg;
    private final StandaloneBuilder b;
    private final EventNameRecorder eventNameRecorder = new EventNameRecorder();
    private final TestLogAppender logAppender = new TestLogAppender("test");
    private final CrawlDriver crawlDriver;

    StandaloneHarnessImpl(StandaloneBuilder b) {
        this.b = b;
        cfg = b.crawlConfig();

        // record events
        if (b.recordEvents()) {
            cfg.addEventListener(eventNameRecorder);
        }

        // record logs
        if (b.recordLogs()) {
            logAppender.startCapture();
        }

        // Crawl Config modifications
        if (StringUtils.isBlank(cfg.getId())) {
            cfg.setId("crawl-" + TimeIdGenerator.next());
        }
        cfg.setFetchers(List.of(Configurable.configure(new MockFetcher())));
        //                .setMaxQueueBatchSize(1)
        //                .setNumThreads(1);
        cfg.getClusterConfig().setClustered(false);
        if (b.configModifier() != null) {
            b.configModifier().accept(cfg);
        }

        // Crawl Driver
        crawlDriver = CrawlTestDriver
                .builder()
                .build();
        if (b.recordCaches()) {
            ((CachesRecorder) crawlDriver.callbacks().getAfterCommand())
                    .setEnabled(true);
        }

    }

    @Override
    public CrawlTestResult launch(String... nodeNames) {
        CrawlTestResult result = null;
        // do launch
        try {
            new Crawler(crawlDriver, cfg).crawl();
        } finally {
            result = getResult();
        }
        return result;
    }

    @Override
    public CompletableFuture<CrawlTestResult> launchAsync(String... nodeNames) {
        return CompletableFuture.supplyAsync(() -> launch(nodeNames));
    }

    @Override
    public CrawlTestResult getResult() {
        return CrawlTestResult.builder()
                .eventNames(eventNameRecorder.getNames())
                .logLines(logAppender.getMessages())
                .caches(((CachesRecorder) crawlDriver
                        .callbacks()
                        .getAfterCommand())
                                .getCaches())
                .build();
    }

    @Override
    public CrawlTestWaitFor waitFor() {
        return waitFor(null);
    }

    @Override
    public CrawlTestWaitFor waitFor(Duration timeout) {
        return new StandaloneWaitFor(
                ofNullable(timeout).orElseGet(() -> Duration.ofSeconds(30)),
                this);
    }

    @Override
    public CrawlConfig getCrawlConfig() {
        return cfg;
    }

    @Override
    public void close() throws IOException {
        if (b.recordEvents()) {
            cfg.removeEventListener(eventNameRecorder);
        }
        if (b.recordLogs()) {
            logAppender.stopCapture();
        }
    }
}
