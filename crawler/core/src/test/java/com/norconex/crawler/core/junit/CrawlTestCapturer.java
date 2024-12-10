/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.junit;

import org.apache.commons.lang3.function.FailableConsumer;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.event.listeners.CrawlerLifeCycleListener;
import com.norconex.crawler.core.grid.GridTestUtil;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerSpecProvider;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

public class CrawlTestCapturer extends CrawlerLifeCycleListener {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrawlCaptures {
        private CrawlerContext context;
        private MemoryCommitter committer;
        private Throwable crawlerError;
    }

    private static final CrawlCaptures captures = new CrawlCaptures();

    public static CrawlCaptures crawlAndCapture(CrawlerConfig config) {
        return crawlAndCapture(config, MockCrawlerSpecProvider.class);
    }

    public static CrawlCaptures crawlAndCapture(
            CrawlerConfig config,
            Class<? extends CrawlerSpecProvider> specProviderClass) {
        if (config.getWorkDir() == null) {
            throw new IllegalStateException(
                    "Crawler working directory must not be null.");
        }
        try {
            var crawler = new MockCrawlerBuilder(config.getWorkDir())
                    .config(config)
                    .specProviderClass(specProviderClass)
                    .crawler();

            var capturer = new CrawlTestCapturer();
            config.addEventListener(capturer);
            crawler.crawl();
            config.removeEventListener(capturer);
            GridTestUtil.waitForGridShutdown();
            return new CrawlCaptures(
                    captures.context,
                    captures.committer,
                    captures.crawlerError);
        } finally {
            reset();
        }
    }

    @SneakyThrows
    public static CrawlCaptures capture(
            @NonNull Crawler crawler,
            @NonNull FailableConsumer<Crawler, Exception> c) {
        try {
            var capturer = new CrawlTestCapturer();
            crawler.getCrawlerConfig().addEventListener(capturer);
            c.accept(crawler);
            crawler.getCrawlerConfig().removeEventListener(capturer);
            return new CrawlCaptures(
                    captures.context,
                    captures.committer,
                    captures.crawlerError);
        } finally {
            reset();
        }
    }

    @Override
    protected void onCrawlerContextInitEnd(CrawlerEvent event) {
        captures.context = event.getSource();
        captures.committer = (MemoryCommitter) event
                .getSource()
                .getConfiguration()
                .getCommitters()
                .get(0);
    }

    @Override
    protected void onCrawlerError(CrawlerEvent event) {
        captures.crawlerError = event.getException();
    }

    private static void reset() {
        captures.context = null;
        captures.committer = null;
        captures.crawlerError = null;
    }
}