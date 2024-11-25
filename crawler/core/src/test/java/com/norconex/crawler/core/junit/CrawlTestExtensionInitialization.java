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

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.function.Consumer;

import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.text.StringUtil;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.mocks.crawler.MockCrawler;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerContext;
import com.norconex.crawler.core.stubs.StubCrawlerConfig;

import lombok.RequiredArgsConstructor;

// Usage defined by CrawlTestInvocationContext, before resolving
// test method parameters.
@RequiredArgsConstructor
public class CrawlTestExtensionInitialization
        implements BeforeTestExecutionCallback {

    private final Class<? extends GridConnector> gridConnectorClass;
    private final CrawlTest annotation;

    @Override
    public void beforeTestExecution(ExtensionContext context)
            throws IOException {
        CrawlTestParameters.set(context, initialize(context));
    }

    public CrawlTestParameters initialize(ExtensionContext context)
            throws IOException {
        // Create a temporary directory before each test
        var tempDir = Files.createTempDirectory("crawltest");

        var crawlerConfig = annotation.randomConfig()
                ? StubCrawlerConfig.randomMemoryCrawlerConfig(tempDir)
                : StubCrawlerConfig.memoryCrawlerConfig(tempDir);

        // apply custom config from text
        StringUtil.ifNotBlank(
                annotation.config(), cfgStr -> BeanMapper.DEFAULT.read(
                        crawlerConfig,
                        new StringReader(cfgStr),
                        Format.fromContent(cfgStr, Format.XML)));

        // apply config modifier from consumer
        if (annotation.configModifier() != null) {
            @SuppressWarnings("unchecked")
            var c = (Consumer<CrawlerConfig>) ClassUtil
                    .newInstance(annotation.configModifier());
            c.accept(crawlerConfig);
        }

        // set grid connector
        crawlerConfig.setGridConnector(
                ClassUtil.newInstance(gridConnectorClass));

        // set listener to capture context instance
        try {
            crawlerConfig
                    .addEventListener(new CrawlTestContextCapturerListener());

            var crawler = MockCrawler.memoryCrawler(
                    tempDir, crawlerConfig, annotation.specProvider());

            if (annotation.run()) {
                crawler.crawl();
            } else {
                MockCrawlerContext.memoryContext(tempDir, crawlerConfig).init();
            }

            return new CrawlTestParameters()
                    .setCrawler(crawler)
                    .setCrawlerConfig(crawlerConfig)
                    .setCrawlerContext(CrawlTestContextCapturerListener.context)
                    .setMemoryCommitter(
                            CrawlTestContextCapturerListener.committer)
                    .setWorkDir(tempDir);
        } finally {
            CrawlTestContextCapturerListener.context = null;
            CrawlTestContextCapturerListener.committer = null;
        }
    }
}