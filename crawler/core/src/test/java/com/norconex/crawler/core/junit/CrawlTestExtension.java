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
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.stream.Stream;

import org.apache.ignite.Ignition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridConnector;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CrawlTestExtension implements
        InvocationInterceptor,
        TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestClass()
                .map(clazz -> clazz.isAnnotationPresent(CrawlTest.class)
                        || context.getTestMethod()
                                .map(method -> method.isAnnotationPresent(
                                        CrawlTest.class))
                                .orElse(false))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext>
            provideTestTemplateInvocationContexts(ExtensionContext context) {
        var annotation = getAnnotation(context);
        var connectorClasses = annotation.gridConnectors();
        return Stream.of(connectorClasses)
                .map(conn -> new CrawlTestInvocationContext(
                        conn, annotation));
    }

    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {

        invocation.proceed();

        var params = CrawlTestParameters.get(extensionContext);

        if (params.getCrawlerConfig()
                .getGridConnector() instanceof IgniteGridConnector) {
            // Ensures ignite has no hold on the temp files before a cleanup
            // attempt of the tempDir is made by Junit. If we have to do this
            // work around too often, modify the ParameterizedGridConnectorTest
            // to handle this.
            waitForGridShutdown();

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
    }

    private void waitForGridShutdown() {
        if (isIgniteRunning()) {
            LOG.info("Ignite still running, stopping it.");
            Ignition.stopAll(true);
        }
        var cnt = 0;
        do {
            Sleeper.sleepMillis(500);
            if (cnt >= 10) {
                LOG.error("Ignite did not appear to shutdown.");
                break;
            }
            cnt++;
        } while (isIgniteRunning());
    }

    private boolean isIgniteRunning() {
        try {
            Ignition.ignite();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private CrawlTest getAnnotation(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> method.getAnnotation(CrawlTest.class))
                .or(() -> context.getTestClass()
                        .map(clazz -> clazz
                                .getAnnotation(CrawlTest.class)))
                .orElseThrow(() -> new IllegalStateException(
                        "Expected @MockCrawler annotation to be present"));
    }
}
