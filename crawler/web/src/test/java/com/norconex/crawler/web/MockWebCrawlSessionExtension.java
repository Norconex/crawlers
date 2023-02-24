/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.support.AnnotationSupport;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.crawler.CrawlerImpl;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.core.session.CrawlSessionEvent;
import com.norconex.crawler.web.MockWebCrawlSession.NoopConfigConsumer;
import com.norconex.crawler.web.crawler.HttpCrawlerConfig;

import lombok.Data;

/**
 * Mocks an initialized crawl session. By default, it is configured
 * with a single crawler having 1 thread.
 */
public class MockWebCrawlSessionExtension implements
            ParameterResolver,
            BeforeAllCallback,
            AfterAllCallback,
            BeforeEachCallback,
            AfterEachCallback {

    public static final String MOCK_CRAWLER_ID = "test-webcrawler-";
    public static final String MOCK_CRAWL_SESSION_ID = "test-webcrawlsession";

    private final TestConfig defaultTestConfig;
    private TestConfig currentTestConfig;

    private CrawlSession crawlSession;

    private boolean typeAnnotated;

    public MockWebCrawlSessionExtension() {
        defaultTestConfig = new TestConfig();
    }
    public MockWebCrawlSessionExtension(
            int numOfCrawlers,
            long timeout,
            Consumer<CrawlSessionConfig> configConsumer) {

        defaultTestConfig = new TestConfig();
        defaultTestConfig.numOfCrawlers = numOfCrawlers;
        defaultTestConfig.configConsumer = configConsumer;
        defaultTestConfig.timeout = timeout;
    }

    @Override
    public void beforeAll(ExtensionContext ctx) throws Exception {
        typeAnnotated = true;
        doBefore(ctx);
    }
    @Override
    public void afterAll(ExtensionContext ctx) throws Exception {
        doAfter(ctx);
    }
    @Override
    public void beforeEach(ExtensionContext ctx) throws Exception {
        doBefore(ctx);
    }
    @Override
    public void afterEach(ExtensionContext ctx) throws Exception {
        doAfter(ctx);
    }

    @Override
    public boolean supportsParameter(
            ParameterContext paramCtx, ExtensionContext extCtx)
                    throws ParameterResolutionException {
        Class<?> type = paramCtx.getParameter().getType();
        return CrawlSession.class.isAssignableFrom(type)
                || CrawlSessionConfig.class.isAssignableFrom(type)
                || Crawler.class.isAssignableFrom(type)
                || CrawlerConfig.class.isAssignableFrom(type)
                ;
    }
    @Override
    public Object resolveParameter(
            ParameterContext paramCtx, ExtensionContext extCtx)
                    throws ParameterResolutionException {
        Class<?> type = paramCtx.getParameter().getType();
        if (CrawlSession.class.isAssignableFrom(type)) {
            return crawlSession;
        }
        if (CrawlSessionConfig.class.isAssignableFrom(type)) {
            return crawlSession.getCrawlSessionConfig();
        }

        // Only return the first crawler or crawler config when multiple exist
        // in session config. To get all, pull them directly from session config
        if (Crawler.class.isAssignableFrom(type)
                && !crawlSession.getCrawlers().isEmpty()) {
            return crawlSession.getCrawlers().get(0);
        }
        if (Crawler.class.isAssignableFrom(type)
                && !crawlSession.getCrawlSessionConfig()
                        .getCrawlerConfigs().isEmpty()) {
            return crawlSession.getCrawlSessionConfig()
                    .getCrawlerConfigs().get(0);
        }
        return null;
    }

    private void doBefore(ExtensionContext ctx) throws Exception {

        if (!typeAnnotated && crawlSession != null) {
            throw new IllegalStateException("""
                    Crawl session already initialized. Have you added\s\
                    the MockWebCrawlSession annotation or extension to\s\
                    both a type and methods?""");
        }

        currentTestConfig = new TestConfig(defaultTestConfig);
        currentTestConfig.tempDir = Files.createTempDirectory("nx-mock-");
        Optional<MockWebCrawlSession> annot = AnnotationSupport.findAnnotation(
                ctx.getElement(), MockWebCrawlSession.class);
        if (annot.isPresent()) {
            currentTestConfig.numOfCrawlers = annot.get().numOfCrawlers();
            currentTestConfig.configConsumer = annot.get().configConsumer()
                    .getDeclaredConstructor().newInstance();
            currentTestConfig.timeout = annot.get().timeout();
        }

        //add listener to config... since event manager is not... at that poitn

        crawlSession = createCrawlSession(currentTestConfig);
        if (crawlSession.getCrawlSessionConfig()
                .getCrawlerConfigs().isEmpty()) {
            throw new IllegalStateException("No crawler configured.");
        }

        var latch = new CountDownLatch(1);

        crawlSession.getCrawlSessionConfig().addEventListener(event -> {
            if (CrawlSessionEvent.CRAWLSESSION_RUN_END.equals(
                    event.getName())
                || CrawlerEvent.CRAWLER_RUN_BEGIN.equals(
                        event.getName())) {
                latch.countDown();
            }
        });

        new Thread(() -> {
            crawlSession.start();
        }).start();

        if (currentTestConfig.timeout > -1) {
            latch.await(currentTestConfig.timeout, TimeUnit.MILLISECONDS);
        } else {
            latch.await();
        }
    }
    private void doAfter(ExtensionContext ctx) throws IOException {
        if (crawlSession != null) {
            crawlSession = null;
            FileUtil.delete(currentTestConfig.tempDir.toFile());
            currentTestConfig.doneStatus = true;
            currentTestConfig = null;
        }
    }

    @Data
    static class TestConfig {
        private boolean doneStatus;
        private Path tempDir;
        private long timeout = 60_000;
        private int numOfCrawlers = 1;
        private Consumer<CrawlSessionConfig> configConsumer =
                new NoopConfigConsumer();
        TestConfig() {}
        TestConfig(TestConfig initConfig) {
            doneStatus = initConfig.doneStatus;
            tempDir = initConfig.tempDir;
            timeout = initConfig.timeout;
            numOfCrawlers = initConfig.numOfCrawlers;
            configConsumer = initConfig.configConsumer;
        }
    }

    static CrawlSession createCrawlSession(TestConfig testCfg) {
        List<CrawlerConfig> crawlerConfigs = new ArrayList<>();
        for (var i = 0; i < testCfg.numOfCrawlers; i++) {
            crawlerConfigs.add(crawlerConfig(i));
        }

        var sessionConfig = new CrawlSessionConfig();
        sessionConfig.setWorkDir(testCfg.tempDir);
        sessionConfig.setId(MOCK_CRAWL_SESSION_ID);
        sessionConfig.setCrawlerConfigs(crawlerConfigs);

        if (testCfg.configConsumer != null) {
            testCfg.configConsumer.accept(sessionConfig);
        }

        return CrawlSession.builder()
            .crawlerFactory((crawlSess, crawlerCfg) ->
                Crawler.builder()
                    .crawlerConfig(crawlerCfg)
                    .crawlSession(crawlSess)
                    .crawlerImpl(crawlerImpl(testCfg))
                    .build()
            )
            .crawlSessionConfig(sessionConfig)
            .build();
    }

    private static CrawlerImpl crawlerImpl(TestConfig testCfg) {
        var builder = WebCrawlSessionLauncher.crawlerImplBuilder();
        builder.afterCrawlerExecution(crawler -> {
            var then = System.currentTimeMillis();
            while (!testCfg.doneStatus) {
                Sleeper.sleepMillis(50);
                if (System.currentTimeMillis() - then > testCfg.timeout
                        && testCfg.timeout > -1) {
                    break;
                }
            }
        });
        return builder.build();
    }

    private static HttpCrawlerConfig crawlerConfig(int index) {
        var crawlerConfig = new HttpCrawlerConfig();
        crawlerConfig.setId(MOCK_CRAWLER_ID + index);
        crawlerConfig.setNumThreads(1);
        crawlerConfig.setCommitters(List.of(new MemoryCommitter()));
        return crawlerConfig;
    }
}

