/* Copyright 2024-2025 Norconex Inc.
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

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core.session.TestSessionUtil;
import com.norconex.crawler.core.stubs.StubCrawlerConfig;
import com.norconex.grid.core.GridConnector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Usage defined by CrawlTestInvocationContext, before resolving
// test method parameters.
@Slf4j
@RequiredArgsConstructor
public class CrawlTestExtensionCallbacks implements
        BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private final Class<? extends GridConnector> gridConnectorClass;
    private final CrawlTest annotation;

    @Override
    public void beforeTestExecution(ExtensionContext context)
            throws Exception {
        CrawlTestParameters.set(context, initialize());
    }

    @Override
    public void afterTestExecution(ExtensionContext context)
            throws Exception {
        destroy(context);
    }

    public CrawlTestParameters initialize()
            throws Exception {
        // Create a temporary directory before each test
        var tempDir = Files.createTempDirectory("crawltest");

        var spec = ClassUtil.newInstance(annotation.driverFactory())
                .get();

        var crawlConfig = annotation.randomConfig()
                ? StubCrawlerConfig.randomMemoryCrawlerConfig(
                        tempDir,
                        spec.crawlerConfigClass(),
                        ClassUtil.newInstance(annotation
                                .randomizer())
                                .get())
                : StubCrawlerConfig.memoryCrawlerConfig(
                        tempDir,
                        spec.crawlerConfigClass());

        // set grid connector
        LOG.info("Setting grid connector: {}", gridConnectorClass);
        GridConnector gridConnector =
                gridConnectorClass.getDeclaredConstructor()
                        .newInstance();
        crawlConfig.setGridConnector(gridConnector);

        // apply custom config from text
        if (StringUtils.isNotBlank(annotation.config())) {
            var cfgStr = StringSubstitutor.replace(
                    annotation.config(),
                    MapUtil.<String, String>toMap(
                            (Object[]) annotation
                                    .vars()));
            spec.beanMapper().read(
                    crawlConfig,
                    new StringReader(cfgStr),
                    Format.fromContent(cfgStr, Format.XML));
        }

        // apply config modifier from consumer
        if (annotation.configModifier() != null) {
            @SuppressWarnings("unchecked")
            var c = (Consumer<CrawlConfig>) ClassUtil
                    .newInstance(annotation
                            .configModifier());
            c.accept(crawlConfig);
        }

        // --- Focus: CRAWL ---
        if (annotation.focus() == Focus.CRAWL) {
            var crawler = new MockCrawlerBuilder(tempDir)
                    .crawlDriver(toCrawlDriver(annotation
                            .driverFactory()))
                    .config(crawlConfig)
                    .crawler();
            var captures = CrawlTestCapturer.capture(crawler,
                    Crawler::crawl);
            return new CrawlTestParameters()
                    .setCrawler(crawler)
                    .setCrawlConfig(crawlConfig)
                    .setCrawlContext(captures.getContext())
                    .setMemoryCommitter(
                            captures.getCommitter())
                    .setWorkDir(tempDir);
        }

        // --- Focus: CONTEXT ---
        if (annotation.focus() == Focus.CONTEXT) {
            var ctx = TestSessionUtil.createCrawlerContext(
                    toCrawlDriver(annotation
                            .driverFactory()),
                    crawlConfig,
                    tempDir);
            ctx.fire(CrawlerEvent.CRAWLER_CRAWL_BEGIN); // simulate
            return new CrawlTestParameters()
                    .setCrawler(null)
                    .setCrawlConfig(crawlConfig)
                    .setCrawlContext(ctx)
                    .setMemoryCommitter(
                            (MemoryCommitter) crawlConfig
                                    .getCommitters()
                                    .get(0))
                    .setWorkDir(tempDir);
        }

        // --- Focus: CONFIG ---
        return new CrawlTestParameters()
                .setCrawler(null)
                .setCrawlConfig(crawlConfig)
                .setCrawlContext(null)
                .setMemoryCommitter(
                        (MemoryCommitter) crawlConfig
                                .getCommitters()
                                .get(0))
                .setWorkDir(tempDir);
    }

    public void destroy(ExtensionContext context)
            throws Exception {

        var params = CrawlTestParameters.get(context);

        if (annotation.focus() == Focus.CONTEXT
                && params.getCrawlContext() != null) {
            // "crawl" focus handles the context already.
            params.getCrawlContext().fire(
                    CrawlerEvent.CRAWLER_CRAWL_END); // simulate
            TestSessionUtil.destroyCrawlerContext(
                    params.getCrawlContext());
        }
        if (params.getMemoryCommitter() != null) {
            params.getMemoryCommitter().clean();
            params.getMemoryCommitter().close();
        }
        if (params.getWorkDir() != null) {
            // Clean up the temporary directory after each test
            var tempDir = params.getWorkDir();
            if (tempDir != null) {
                Files.walk(tempDir)
                        // Delete files before directories
                        .sorted((path1, path2) -> path2
                                .compareTo(path1))
                        .forEach(path -> {
                            try {
                                FileUtil.delete(path
                                        .toFile());
                            } catch (IOException e) {
                                throw new RuntimeException(
                                        "Failed to delete file: "
                                                + path,
                                        e);
                            }
                        });
            }
        }
    }

    private CrawlDriver toCrawlDriver(
            Class<? extends Supplier<CrawlDriver>> supplierClass) {
        return ClassUtil.newInstance(supplierClass).get();
    }
}
