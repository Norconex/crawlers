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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.text.StringUtil;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.mocks.crawler.MockCrawler;
import com.norconex.crawler.core.stubs.StubCrawlerConfig;

@Disabled
class CrawlerTestExtension implements
        InvocationInterceptor,
        TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestClass()
                .map(clazz -> clazz.isAnnotationPresent(CrawlerTest.class)
                        || context.getTestMethod()
                                .map(method -> method.isAnnotationPresent(
                                        CrawlerTest.class))
                                .orElse(false))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext>
            provideTestTemplateInvocationContexts(ExtensionContext context) {
        var annotation = getAnnotation(context);
        var connectorClasses = annotation.gridConnectors();
        return Stream.of(connectorClasses)
                .map(conn -> new CrawlerTestInvocationContext(
                        conn, annotation, context));
    }

    private CrawlerTest getAnnotation(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> method.getAnnotation(CrawlerTest.class))
                .or(() -> context.getTestClass()
                        .map(clazz -> clazz
                                .getAnnotation(CrawlerTest.class)))
                .orElseThrow(() -> new IllegalStateException(
                        "Expected @MockCrawler annotation to be present"));
    }

    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extCtx) throws Throwable {

        var templateInvocationContext =
                CrawlerTestInvocationContext.get(extCtx);

        var gridConnectorClass =
                templateInvocationContext.getGridConnectorClass();
        var annot = templateInvocationContext.getAnnotation();

        // Create a temporary directory before each test
        var tempDir = Files.createTempDirectory("crawler-core");

        var crawlerConfig = annot.randomConfig()
                ? StubCrawlerConfig.randomMemoryCrawlerConfig(tempDir)
                : StubCrawlerConfig.memoryCrawlerConfig(tempDir);

        // apply custom config from text
        StringUtil.ifNotBlank(
                annot.config(), cfgStr -> BeanMapper.DEFAULT.read(
                        crawlerConfig,
                        new StringReader(cfgStr),
                        Format.fromContent(cfgStr, Format.XML)));

        // apply config modifier from consumer
        if (annot.configModifier() != null) {
            @SuppressWarnings("unchecked")
            var c = (Consumer<CrawlerConfig>) ClassUtil
                    .newInstance(annot.configModifier());
            c.accept(crawlerConfig);
        }

        // set grid connector
        crawlerConfig.setGridConnector(
                ClassUtil.newInstance(gridConnectorClass));

        var crawler = MockCrawler.memoryCrawler(
                tempDir, crawlerConfig, annot.specProvider());

        CrawlerTestInvocationContext.setCrawler(extCtx, crawler);
        crawler.crawl();
        invocation.proceed();

        // Clean up the temporary directory after each test
        if (tempDir != null) {
            Files.walk(tempDir)
                    // Delete files before directories
                    .sorted((path1, path2) -> path2.compareTo(path1))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException(
                                    "Failed to delete file: " + path, e);
                        }
                    });
        }

    }
}
