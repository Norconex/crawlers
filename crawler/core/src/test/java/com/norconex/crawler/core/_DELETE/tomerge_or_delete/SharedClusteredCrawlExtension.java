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
package com.norconex.crawler.core._DELETE.tomerge_or_delete;

import java.util.function.Supplier;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core._DELETE.crawler.ClusteredCrawlOuput;
import com.norconex.crawler.core._DELETE.crawler.ClusteredCrawler;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * JUnit 5 extension that creates a shared cluster once before all tests
 * in a class and reuses it across all test methods. This significantly
 * reduces test execution time by eliminating repeated cluster startup
 * overhead.
 * </p>
 * <p>
 * The cluster is started during {@code @BeforeAll} and shut down during
 * {@code @AfterAll}, so cluster startup/shutdown time does NOT count
 * against individual test method timeouts.
 * </p>
 * <p>
 * Test methods can inject {@link ClusteredCrawlOuput} as a parameter
 * to access the cluster execution results.
 * </p>
 */
@Slf4j
public class SharedClusteredCrawlExtension
        implements BeforeAllCallback, AfterAllCallback,
        ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace
                    .create(SharedClusteredCrawlExtension.class);

    private static final String CLUSTER_OUTPUT_KEY = "clusterOutput";

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        var annotation = context.getRequiredTestClass()
                .getAnnotation(SharedClusteredCrawl.class);

        if (annotation == null) {
            throw new IllegalStateException(
                    "Test class must be annotated with @SharedClusteredCrawl");
        }

        LOG.info("Creating shared cluster for test class: {}",
                context.getRequiredTestClass().getSimpleName());

        @SuppressWarnings("unchecked")
        var driverClass = (Class<? extends Supplier<CrawlDriver>>) annotation
                .driverSupplierClass();
        var nodes = annotation.nodes();
        var threads = annotation.threads();
        var extraArgs = annotation.extraArgs();

        // Create the cluster and run the crawler
        var output = ClusteredCrawler.builder()
                .driverSupplierClass(driverClass)
                .build()
                .launch(nodes, new CrawlConfig()
                        .setNumThreads(threads),
                        extraArgs);

        // Store the output in the extension context for test methods to access
        context.getStore(NAMESPACE).put(CLUSTER_OUTPUT_KEY, output);

        LOG.info("Shared cluster ready for test class: {}",
                context.getRequiredTestClass().getSimpleName());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        LOG.info("Cleaning up shared cluster for test class: {}",
                context.getRequiredTestClass().getSimpleName());

        // Cluster cleanup happens automatically via testcontainers' shutdown hooks
        // Just remove from our storage
        context.getStore(NAMESPACE).remove(CLUSTER_OUTPUT_KEY);
    }

    @Override
    public boolean supportsParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) {
        return parameterContext.getParameter()
                .getType()
                .equals(ClusteredCrawlOuput.class);
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) {
        var output = extensionContext.getStore(NAMESPACE)
                .get(CLUSTER_OUTPUT_KEY,
                        ClusteredCrawlOuput.class);

        if (output == null) {
            throw new IllegalStateException(
                    "Cluster output not available. Did the cluster startup fail?");
        }

        return output;
    }
}
