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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import com.norconex.commons.lang.file.FileUtil;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridTestUtil;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridConnector;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.util.ExtensibleAnnotationFinder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CrawlTestExtension implements
        InvocationInterceptor,
        TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return findAnnotation(context.getTestClass()).isPresent()
                || findAnnotation(context.getTestMethod()).isPresent();
    }

    @Override
    public Stream<TestTemplateInvocationContext>
            provideTestTemplateInvocationContexts(ExtensionContext context) {
        var annotation = findAnnotation(context.getTestMethod()).orElseThrow();
        var connectorClasses = annotation.gridConnectors();
        return Stream.of(connectorClasses)
                .map(conn -> new CrawlTestInvocationContext(
                        conn, annotation));
    }

    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext context) throws Throwable {

        invocation.proceed();

        var params = CrawlTestParameters.get(context);

        // close context + grid if it was the focus
        // (for CRAWL the crawler does it).
        if (params.getCrawler() == null) {
            findAnnotation(context.getTestMethod()).ifPresent(annot -> {
                if (annot.focus() == Focus.CONTEXT) {
                    var ctx = params.getCrawlerContext();
                    ctx.fire(CrawlerEvent.CRAWLER_CRAWL_END); // simulate
                    ctx.close();
                    ctx.getGrid().close();
                }
            });
        }

        if (params.getCrawlerConfig()
                .getGridConnector() instanceof IgniteGridConnector) {
            // Ensures ignite has no hold on the temp files before a cleanup
            // attempt of the tempDir is made by Junit. If we have to do this
            // work around too often, modify the ParameterizedGridConnectorTest
            // to handle this.
            GridTestUtil.waitForGridShutdown();
        }
        // Clean up the temporary directory after each test
        var tempDir = params.getWorkDir();
        if (tempDir != null) {
            Files.walk(tempDir)
                    // Delete files before directories
                    .sorted((path1, path2) -> path2.compareTo(path1))
                    .forEach(path -> {
                        try {
                            FileUtil.delete(path.toFile());
                        } catch (IOException e) {
                            throw new RuntimeException(
                                    "Failed to delete file: " + path, e);
                        }
                    });
        }
    }

    private Optional<CrawlTest> findAnnotation(
            Optional<? extends AnnotatedElement> el) {
        return ExtensibleAnnotationFinder.find(
                el.orElse(null), CrawlTest.class);
    }
}
