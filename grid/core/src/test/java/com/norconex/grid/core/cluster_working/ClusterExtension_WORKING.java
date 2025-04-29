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
package com.norconex.grid.core.cluster_working;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.cluster.WithCluster.Default;

public class ClusterExtension_WORKING
        implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final String TEMP_DIR = "tempDir";
    private static final String CLUSTER = "cluster";
    private static final String NUM_NODES = "numNodes";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // Create a temp directory for this test
        var tempDir = Files.createTempDirectory("gridtest-");
        getStore(context).put(TEMP_DIR, tempDir); // Store it for cleanup later

        // Find the correct @ClusterTest annotation and its connector factory
        var effectiveAnnotation = resolveEffectiveClusterAnnotation(context);
        var connFactory = effectiveAnnotation
                .connectorFactory()
                .getDeclaredConstructor()
                .newInstance();

        // Initialize the cluster
        var cluster = new Cluster(connFactory, tempDir);
        getStore(context).put(CLUSTER, cluster);
        getStore(context).put(NUM_NODES, effectiveAnnotation.numNodes());

    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        // Retrieve the stored cluster and tempDir
        var store = getStore(context);

        var cluster = store.remove(CLUSTER, Cluster.class);
        if (cluster != null) {
            cluster.close(); // Close the cluster gracefully
        }

        var tempDir = store.remove(TEMP_DIR, Path.class);
        if (tempDir != null) {
            FileUtils.deleteDirectory(tempDir.toFile()); // Clean up temp directory
        }
    }

    @Override
    public boolean supportsParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) {

        var store = getStore(extensionContext);
        var numNodes = (Integer) store.get(NUM_NODES);

        Class<?> type = parameterContext.getParameter().getType();

        // Check for specific conditions for Cluster, Grid, List, and Path types
        if (type.isAssignableFrom(Path.class)
                || (type.isAssignableFrom(Cluster.class) && numNodes <= 0)
                || (type.isAssignableFrom(Grid.class) && numNodes == 1)
                || (isGridList(parameterContext) && numNodes > 1)) {
            return true;
        }
        return false;
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) {

        var store = getStore(extensionContext);
        var numNodes = (Integer) store.get(NUM_NODES);

        Class<?> type = parameterContext.getParameter().getType();

        if (type.isAssignableFrom(Cluster.class)) {
            return store.get(CLUSTER);
        }
        if (type.isAssignableFrom(Grid.class)) {
            return ((Cluster) store.get(CLUSTER)).oneNewNode();
        }
        if (isGridList(parameterContext)) {
            return ((Cluster) store.get(CLUSTER)).newNodes(numNodes);
        }

        if (type.isAssignableFrom(Path.class)) {
            return store.get(TEMP_DIR);
        }
        throw new ParameterResolutionException(
                "Unsupported parameter type: " + type);
    }

    private Optional<ClusterTest>
            findClusterAnnotation(ExtensionContext context) {
        // First, check the annotations directly on the test method or class
        Optional<ClusterTest> annotation = context.getElement()
                .flatMap(this::findGridAnnotationOnElement);

        // If no annotation is found on the method/class, check the test class
        if (annotation.isEmpty()) {
            annotation = context.getTestClass()
                    .flatMap(this::findGridAnnotationOnElement);
        }

        // If still no annotation found, check parent classes for composed
        // annotations
        if (annotation.isEmpty()) {
            annotation = findClusterAnnotationOnParent(context);
        }

        return annotation;
    }

    private Optional<ClusterTest>
            findGridAnnotationOnElement(AnnotatedElement element) {
        // Direct check for @ClusterTest on the current element
        // (method or class)
        var clusterTest = element.getAnnotation(ClusterTest.class);
        if (clusterTest != null) {
            return Optional.of(clusterTest);
        }

        // If @ClusterTest is not found, check for composed annotations
        for (Annotation annotation : element.getAnnotations()) {
            if (annotation.annotationType()
                    .isAnnotationPresent(ClusterTest.class)) {
                // If a composed annotation like @CoreClusterTest exists,
                // return it
                return Optional.of(annotation.annotationType()
                        .getAnnotation(ClusterTest.class));
            }
        }

        return Optional.empty();
    }

    private Optional<ClusterTest>
            findClusterAnnotationOnParent(ExtensionContext context) {
        Class<?> current = context.getTestClass().orElse(null);

        // Walk up the class hierarchy to check for composed annotations
        while (current != null && current != Object.class) {
            // Check the annotations on the parent class
            for (Annotation annotation : current.getAnnotations()) {
                if (annotation.annotationType()
                        .isAnnotationPresent(ClusterTest.class)) {
                    return Optional.of(annotation.annotationType()
                            .getAnnotation(ClusterTest.class));
                }
            }
            current = current.getSuperclass();
        }

        return Optional.empty();
    }

    private ClusterTest
            resolveEffectiveClusterAnnotation(ExtensionContext context) {
        var clusterTest = findClusterAnnotation(context)
                .orElseThrow(() -> new IllegalStateException(
                        "@ClusterTest is missing"));

        Class<? extends ClusterConnectorFactory> effectiveConnectorFactory =
                clusterTest.connectorFactory();
        var effectiveNumNodes = clusterTest.numNodes();

        var currentContext = context.getParent().orElse(null);

        while ((effectiveConnectorFactory == Default.class
                || effectiveNumNodes == -1)
                && currentContext != null) {
            var parentAnnotation = findClusterAnnotation(currentContext);
            if (parentAnnotation.isPresent()) {
                var parent = parentAnnotation.get();
                if (effectiveConnectorFactory == Default.class
                        && parent.connectorFactory() != Default.class) {
                    effectiveConnectorFactory = parent.connectorFactory();
                }
                if (effectiveNumNodes == -1 && parent.numNodes() >= 0) {
                    effectiveNumNodes = parent.numNodes();
                }
            }
            currentContext = currentContext.getParent().orElse(null);
        }

        // Still not resolved?
        if (effectiveConnectorFactory == Default.class) {
            throw new IllegalStateException(
                    "No parent found with a non-default connectorFactory for @ClusterTest");
        }

        // Dynamically create a ClusterTest implementation with resolved values
        final var finalConnectorFactory = effectiveConnectorFactory;
        final var finalNumNodes = effectiveNumNodes;

        return new ClusterTest() {
            @Override
            public Class<? extends ClusterConnectorFactory> connectorFactory() {
                return finalConnectorFactory;
            }

            @Override
            public int numNodes() {
                return finalNumNodes;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ClusterTest.class;
            }
        };
    }

    private ExtensionContext.Store getStore(ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(getClass(),
                context.getUniqueId()));
    }

    private boolean isGridList(ParameterContext parameterContext) {
        Class<?> type = parameterContext.getParameter().getType();
        if (type.isAssignableFrom(List.class)) {
            var genericType = (ParameterizedType) parameterContext
                    .getParameter().getParameterizedType();
            var actualTypeArguments = genericType.getActualTypeArguments();
            if (actualTypeArguments.length == 1
                    && actualTypeArguments[0].equals(Grid.class)) {
                return true;
            }
        }
        return false;
    }
}
