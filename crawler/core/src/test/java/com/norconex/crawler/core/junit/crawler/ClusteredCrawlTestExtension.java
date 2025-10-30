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
package com.norconex.crawler.core.junit.crawler;

import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.junit.WithLogLevel;
import com.norconex.crawler.core.junit.cluster.SharedCluster;
import com.norconex.crawler.core.mocks.crawler.MockCrawlDriverFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * Extension that enables parameterized cluster testing with different node
 * counts. Automatically wraps test execution with {@link SharedCluster}
 * for container reuse and performance optimization.
 *
 * <p>Test methods can inject a {@link ClusteredCrawlContext} parameter
 * which provides access to the cluster client, crawl output, and node count.
 * The cluster is automatically created and managed.
 *
 * <p>Example usage:
 * <pre>
 * &#64;ClusteredCrawlTest(nodes = {2, 3, 5})
 * void testCluster(ClusteredCrawlContext context) {
 *     // SharedCluster.withNodes() is called automatically!
 *     assertThat(context.getClient().getNodes()).hasSize(
 *             context.getNodeCount());
 *     assertThat(context.getOuput()).isNotNull();
 * }
 * </pre>
 */
@Slf4j
public class ClusteredCrawlTestExtension
        implements ParameterResolver, TestTemplateInvocationContextProvider,
        InvocationInterceptor {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace
                    .create(ClusteredCrawlTestExtension.class);

    private static final String NODE_COUNT_KEY = "nodeCount";
    private static final String CRAWL_CONTEXT_KEY = "crawlContext";

    @Override
    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {

        // Get the node count for this iteration
        Integer nodeCount = extensionContext.getStore(NAMESPACE)
                .getOrDefault(NODE_COUNT_KEY, Integer.class, 1);

        // Check for @WithLogLevel annotation
        var logLevelAnnotations = findLogLevelAnnotations(extensionContext);

        // Wrap the test execution in SharedCluster.withNodes()
        SharedCluster.withNodes(nodeCount, client -> {
            // Get the EXISTING context that was created during parameter resolution
            var context = extensionContext.getStore(NAMESPACE)
                    .get(CRAWL_CONTEXT_KEY, ClusteredCrawlContext.class);

            if (context == null) {
                throw new IllegalStateException(
                        "ClusteredCrawlContext not found in extension store. "
                                + "This should have been created during "
                                + "parameter resolution.");
            }

            // Populate the existing context with client, output, and node count
            context.setClient(client);
            context.setNodeCount(nodeCount);

            try {
                // Apply log levels before test execution
                applyLogLevels(logLevelAnnotations);

                // Launch the crawler and store output in context BEFORE test
                // execution
                var output = launchCrawler(
                        extensionContext, nodeCount, logLevelAnnotations);
                if (output != null) {
                    context.setOuput(output);
                }

                // Execute the actual test method (which already has reference
                // to context)
                invocation.proceed();
            } catch (Throwable e) {
                // Re-throw as runtime exception to propagate through lambda
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                if (e instanceof Error) {
                    throw (Error) e;
                }
                throw new RuntimeException(e);
            } finally {
                // Restore log levels after test execution
                restoreLogLevels(logLevelAnnotations);
                // Clean up
                extensionContext.getStore(NAMESPACE)
                        .remove(CRAWL_CONTEXT_KEY);
            }
        });
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getTestMethod()
                .map(m -> m.isAnnotationPresent(ClusteredCrawlTest.class))
                .orElse(false);
    }

    @Override
    public Stream<TestTemplateInvocationContext>
            provideTestTemplateInvocationContexts(
                    ExtensionContext context) {
        ClusteredCrawlTest annotation = context.getTestMethod()
                .map(m -> m.getAnnotation(ClusteredCrawlTest.class))
                .orElseThrow();

        int[] nodeCounts = annotation.nodes();

        return Arrays.stream(nodeCounts)
                .mapToObj(nodeCount -> new TestTemplateInvocationContext() {
                    @Override
                    public String getDisplayName(int invocationIndex) {
                        return String.format("[%d nodes]", nodeCount);
                    }

                    @Override
                    public List<Extension> getAdditionalExtensions() {
                        // Only BeforeEachCallback - ParameterResolver
                        // handled by top-level extension
                        return List.of(
                                (BeforeEachCallback) ctx -> {
                                    // Store node count for interceptor to use
                                    ctx.getStore(NAMESPACE).put(NODE_COUNT_KEY,
                                            nodeCount);
                                });
                    }
                });
    }

    @Override
    public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
        return pc.getParameter().getType().equals(ClusteredCrawlContext.class);
    }

    @Override
    public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
        // Resolve ClusteredCrawlContext - create instance now, will be
        // populated later
        if (pc.getParameter().getType().equals(ClusteredCrawlContext.class)) {
            var context = ec.getStore(NAMESPACE)
                    .get(CRAWL_CONTEXT_KEY, ClusteredCrawlContext.class);
            if (context == null) {
                // Create new context - will be populated by interceptor
                context = new ClusteredCrawlContext();
                ec.getStore(NAMESPACE).put(CRAWL_CONTEXT_KEY, context);
            }
            return context;
        }

        throw new IllegalStateException(
                "Unsupported parameter type: " + pc.getParameter().getType());
    }

    private ClusteredCrawlOuput launchCrawler(
            ExtensionContext extensionContext,
            int nodeCount,
            List<WithLogLevel> logLevelAnnotations) {

        var testMethod = extensionContext.getRequiredTestMethod();
        var annotation = testMethod.getAnnotation(ClusteredCrawlTest.class);

        // Get the driver supplier class
        Class<? extends Supplier<CrawlDriver>> driverSupplierClass =
                annotation.driverSupplierClass();
        if (driverSupplierClass == null) {
            driverSupplierClass = MockCrawlDriverFactory.class;
        }

        // Launch the crawler with log levels passed separately
        return ClusteredCrawler.builder()
                .driverSupplierClass(driverSupplierClass)
                .logLevels(logLevelAnnotations)
                .build()
                .launch(nodeCount,
                        resolveCrawlConfig(annotation, driverSupplierClass),
                        annotation.cliArgs());
    }

    private CrawlConfig resolveCrawlConfig(
            ClusteredCrawlTest annotation,
            Class<? extends Supplier<CrawlDriver>> driverSupplierClass) {
        if (ClusteredCrawlTest.NO_CONFIG.equals(annotation.config())) {
            return null;
        }
        var driver = ClassUtil.newInstance(driverSupplierClass).get();
        var config = ClassUtil.newInstance(driver.crawlerConfigClass());

        // apply custom config from text
        if (StringUtils.isNotBlank(annotation.config())) {
            var cfgStr = StringSubstitutor.replace(
                    annotation.config(),
                    MapUtil.<String, String>toMap(
                            (Object[]) annotation.vars()));
            driver.beanMapper().read(
                    config,
                    new StringReader(cfgStr),
                    Format.fromContent(cfgStr, Format.XML));
        }

        // apply config modifier
        if (annotation.configModifier() != null) {
            @SuppressWarnings("unchecked")
            var c = (Consumer<CrawlConfig>) ClassUtil
                    .newInstance(annotation.configModifier());
            c.accept(config);
        }

        //TODO always add a memory committer, or never add it, using
        // FS committer instead so values can be returned in cluster
        // response?
        if (config.getCommitters().isEmpty()) {
            config.setCommitters(List.of(new MemoryCommitter()));
        }
        return config;
    }

    private List<WithLogLevel>
            findLogLevelAnnotations(ExtensionContext context) {
        var annotations = new ArrayList<WithLogLevel>();

        // Check method
        var method = context.getTestMethod().orElse(null);
        if (method != null) {
            // Check for container annotation (repeatable)
            var container =
                    method.getAnnotation(WithLogLevel.WithLogLevels.class);
            if (container != null) {
                annotations.addAll(Arrays.asList(container.value()));
            } else {
                var single = method.getAnnotation(WithLogLevel.class);
                if (single != null) {
                    annotations.add(single);
                }
            }
        }

        // Check class
        var testClass = context.getTestClass().orElse(null);
        if (testClass != null) {
            var container =
                    testClass.getAnnotation(WithLogLevel.WithLogLevels.class);
            if (container != null) {
                annotations.addAll(Arrays.asList(container.value()));
            } else {
                var single = testClass.getAnnotation(WithLogLevel.class);
                if (single != null) {
                    annotations.add(single);
                }
            }
        }

        return annotations;
    }

    private void applyLogLevels(List<WithLogLevel> annotations) {
        // Convert log level annotations to system properties
        // that can be passed to containers
        for (WithLogLevel annotation : annotations) {
            String level = annotation.value();
            for (Class<?> clazz : annotation.classes()) {
                // Set log level via system property
                String propertyKey = "log4j.logger." + clazz.getName();
                String propertyValue = level;

                // Store for passing to containers or use directly
                System.setProperty(propertyKey, propertyValue);
            }
        }
    }

    private void restoreLogLevels(List<WithLogLevel> annotations) {
        // Clean up system properties
        for (WithLogLevel annotation : annotations) {
            for (Class<?> clazz : annotation.classes()) {
                String propertyKey = "log4j.logger." + clazz.getName();
                System.clearProperty(propertyKey);
            }
        }
    }
}
