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

import java.io.StringReader;
import java.nio.file.Files;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerBuilder;
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
            throws Exception {
        CrawlTestParameters.set(context, initialize(context));
    }

    public CrawlTestParameters initialize(ExtensionContext context)
            throws Exception {
        // Create a temporary directory before each test
        var tempDir = Files.createTempDirectory("crawltest");

        var spec = ClassUtil.newInstance(annotation.specProvider()).get();

        var crawlerConfig = annotation.randomConfig()
                ? StubCrawlerConfig.randomMemoryCrawlerConfig(
                        tempDir,
                        spec.crawlerConfigClass(),
                        ClassUtil.newInstance(annotation.randomizer()).get())
                : StubCrawlerConfig.memoryCrawlerConfig(
                        tempDir,
                        spec.crawlerConfigClass());

        // apply custom config from text
        if (StringUtils.isNotBlank(annotation.config())) {
            var cfgStr = StringSubstitutor.replace(
                    annotation.config(),
                    MapUtil.<String, String>toMap(
                            (Object[]) annotation.vars()));
            spec.beanMapper().read(
                    crawlerConfig,
                    new StringReader(cfgStr),
                    Format.fromContent(cfgStr, Format.XML));
        }

        // apply config modifier from consumer
        if (annotation.configModifier() != null) {
            @SuppressWarnings("unchecked")
            var c = (Consumer<CrawlerConfig>) ClassUtil
                    .newInstance(annotation.configModifier());
            c.accept(crawlerConfig);
        }

        // set grid connector
        GridConnector gridConnector = ClassUtil.newInstance(gridConnectorClass);
        crawlerConfig.setGridConnector(gridConnector);

        // --- Focus: CRAWL ---
        if (annotation.focus() == Focus.CRAWL) {
            var crawler = new MockCrawlerBuilder(tempDir)
                    .config(crawlerConfig)
                    .specProviderClass(annotation.specProvider())
                    .crawler();
            var captures = CrawlTestCapturer.capture(crawler, crwl -> {
                crwl.crawl();
            });
            return new CrawlTestParameters()
                    .setCrawler(crawler)
                    .setCrawlerConfig(crawlerConfig)
                    .setCrawlerContext(captures.getContext())
                    .setMemoryCommitter(captures.getCommitter())
                    .setWorkDir(tempDir);
        }

        // --- Focus: CONTEXT ---
        if (annotation.focus() == Focus.CONTEXT) {
            var ctx = new MockCrawlerBuilder(tempDir)
                    .config(crawlerConfig)
                    .specProviderClass(annotation.specProvider())
                    .crawlerContext();

            ctx.init();
            ctx.fire(CrawlerEvent.CRAWLER_CRAWL_BEGIN); // simulate
            return new CrawlTestParameters()
                    .setCrawler(null)
                    .setCrawlerConfig(crawlerConfig)
                    .setCrawlerContext(ctx)
                    .setMemoryCommitter((MemoryCommitter) crawlerConfig
                            .getCommitters().get(0))
                    .setWorkDir(tempDir);
        }

        // --- Focus: CONFIG ---
        return new CrawlTestParameters()
                .setCrawler(null)
                .setCrawlerConfig(crawlerConfig)
                .setCrawlerContext(null)
                .setMemoryCommitter((MemoryCommitter) crawlerConfig
                        .getCommitters().get(0))
                .setWorkDir(tempDir);
    }
}
