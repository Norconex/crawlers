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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.testcontainers.containers.GenericContainer;

public class ClusteredCrawlTestExtension
        implements BeforeAllCallback, AfterAllCallback,
        BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace
                    .create(ClusteredCrawlTestExtension.class);

    // Helper to find annotation on method or class
    private Optional<ClusteredCrawlTest> findAnnotation(ExtensionContext ctx) {
        // Check method first
        if (ctx.getElement().isPresent()) {
            var ann = ctx.getElement().get()
                    .getAnnotation(ClusteredCrawlTest.class);
            if (ann != null) {
                return Optional.of(ann);
            }
        }
        // Then check class
        if (ctx.getTestClass().isPresent()) {
            var ann = ctx.getTestClass().get()
                    .getAnnotation(ClusteredCrawlTest.class);
            if (ann != null) {
                return Optional.of(ann);
            }
        }
        return Optional.empty();
    }

    // Helper to create containers
    private List<GenericContainer<?>>
            startContainers(ClusteredCrawlTest config) {
        var classesPath = new File("target/classes").getAbsolutePath();
        var testClassesPath =
                new File("target/test-classes").getAbsolutePath();
        var dependencyPath = new File("target/dependency").getAbsolutePath();
        List<GenericContainer<?>> containers = new ArrayList<>();
        for (var i = 0; i < config.numInstances(); i++) {
            GenericContainer<?> container =
                    new GenericContainer<>("eclipse-temurin:17-jre")
                            .withFileSystemBind(classesPath, "/app/classes")
                            .withFileSystemBind(testClassesPath,
                                    "/app/test-classes")
                            .withFileSystemBind(dependencyPath,
                                    "/app/dependency")
                            .withCommand("java", "-cp",
                                    "/app/classes:/app/test-classes:/app/dependency/*",
                                    config.mainClass());
            container.start();
            containers.add(container);
        }
        return containers;
    }

    //    ClassGraph().scan().getURLS()   Performs a scan and returns a list of URL objects for all classpath elements (JARs and directories).
    //    ClassGraph().scan().getUniqueClasspathElements()    Returns a list of File objects representing the unique directories and JARs on the classpath.
    //
    // Store key: class or method unique id
    private String storeKey(ExtensionContext ctx) {
        return ctx.getUniqueId();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        // Only start containers if annotation is on class
        var ann = context.getTestClass()
                .map(c -> c.getAnnotation(ClusteredCrawlTest.class))
                .orElse(null);
        if (ann != null) {
            var containers = startContainers(ann);
            context.getStore(NAMESPACE).put(storeKey(context), containers);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        var containers = (List<GenericContainer<?>>) context.getStore(NAMESPACE)
                .remove(storeKey(context));
        if (containers != null) {
            containers.forEach(GenericContainer::close);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        // If annotation is on method (and not class), start containers
        var ann = context.getElement()
                .map(e -> e.getAnnotation(ClusteredCrawlTest.class))
                .orElse(null);
        if (ann != null &&
                context.getTestClass()
                        .map(c -> c.getAnnotation(ClusteredCrawlTest.class))
                        .isEmpty()) {
            var containers = startContainers(ann);
            context.getStore(NAMESPACE).put(storeKey(context), containers);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var containers = (List<GenericContainer<?>>) context.getStore(NAMESPACE)
                .remove(storeKey(context));
        if (containers != null) {
            containers.forEach(GenericContainer::close);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
        return pc.getParameter().getType().equals(List.class);
    }

    @Override
    public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
        var containers = (List<GenericContainer<?>>) ec.getStore(NAMESPACE)
                .get(storeKey(ec));
        if (containers == null) {
            throw new IllegalStateException(
                    "No containers found for injection. " +
                            "Did you forget to annotate the test class or method with @ClusteredCrawlTest?");
        }
        return containers;
    }
}
