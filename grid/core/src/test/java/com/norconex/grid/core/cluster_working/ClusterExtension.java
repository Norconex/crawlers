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

public class ClusterExtension
        implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final String TEMP_DIR = "tempDir";
    private static final String CLUSTER = "cluster";
    private static final String NUM_NODES = "numNodes";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        var tempDir = Files.createTempDirectory("gridtest-");
        var store = getStore(context);
        store.put(TEMP_DIR, tempDir);

        var annotation = resolveEffectiveClusterAnnotation(context);
        var connectorFactory = annotation.connectorFactory()
                .getDeclaredConstructor()
                .newInstance();

        var cluster = new Cluster(connectorFactory, tempDir);
        store.put(CLUSTER, cluster);
        store.put(NUM_NODES, annotation.numNodes());
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        var store = getStore(context);

        Optional.ofNullable(store.remove(CLUSTER, Cluster.class))
                .ifPresent(Cluster::close);

        Optional.ofNullable(store.remove(TEMP_DIR, Path.class))
                .ifPresent(temp -> {
                    try {
                        FileUtils.deleteDirectory(temp.toFile());
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to delete temp directory: " + temp, e);
                    }
                });
    }

    @Override
    public boolean supportsParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) {

        var numNodes = getStore(extensionContext).get(NUM_NODES, Integer.class);
        var type = parameterContext.getParameter().getType();

        return type.isAssignableFrom(Path.class)
                || (type.isAssignableFrom(Cluster.class) && numNodes <= 0)
                || (type.isAssignableFrom(Grid.class) && numNodes == 1)
                || (isGridList(parameterContext) && numNodes > 1);
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) {

        var store = getStore(extensionContext);
        var type = parameterContext.getParameter().getType();

        if (type.isAssignableFrom(Cluster.class)) {
            return store.get(CLUSTER);
        }
        if (type.isAssignableFrom(Grid.class)) {
            return store.get(CLUSTER, Cluster.class).oneNewNode();
        }
        if (isGridList(parameterContext)) {
            var numNodes = store.get(NUM_NODES, Integer.class);
            return store.get(CLUSTER, Cluster.class).newNodes(numNodes);
        }
        if (type.isAssignableFrom(Path.class)) {
            return store.get(TEMP_DIR);
        }
        throw new ParameterResolutionException(
                "Unsupported parameter type: " + type);
    }

    private Optional<ClusterTest>
            findClusterAnnotation(ExtensionContext context) {
        return context.getElement().flatMap(this::findGridAnnotationOnElement)
                .or(() -> context.getTestClass()
                        .flatMap(this::findGridAnnotationOnElement))
                .or(() -> findClusterAnnotationOnParent(context));
    }

    private Optional<ClusterTest>
            findGridAnnotationOnElement(AnnotatedElement element) {
        var clusterTest = element.getAnnotation(ClusterTest.class);
        if (clusterTest != null) {
            return Optional.of(clusterTest);
        }

        for (var annotation : element.getAnnotations()) {
            var type = annotation.annotationType();
            var composed = type.getAnnotation(ClusterTest.class);
            if (composed != null) {
                return Optional.of(composed);
            }
        }
        return Optional.empty();
    }

    private Optional<ClusterTest>
            findClusterAnnotationOnParent(ExtensionContext context) {
        var current = context.getTestClass().orElse(null);
        while (current != null && current != Object.class) {
            for (var annotation : current.getAnnotations()) {
                var composed = annotation.annotationType()
                        .getAnnotation(ClusterTest.class);
                if (composed != null) {
                    return Optional.of(composed);
                }
            }
            current = current.getSuperclass();
        }
        return Optional.empty();
    }

    private ClusterTest
            resolveEffectiveClusterAnnotation(ExtensionContext context) {
        var annotation = findClusterAnnotation(context)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing @ClusterTest annotation"));

        var connectorFactory = annotation.connectorFactory();
        var numNodes = annotation.numNodes();
        var parentContext = context.getParent().orElse(null);

        while ((connectorFactory == Default.class || numNodes == -1)
                && parentContext != null) {
            var parentAnnotation = findClusterAnnotation(parentContext);
            if (parentAnnotation.isPresent()) {
                var parent = parentAnnotation.get();
                if (connectorFactory == Default.class
                        && parent.connectorFactory() != Default.class) {
                    connectorFactory = parent.connectorFactory();
                }
                if (numNodes == -1 && parent.numNodes() >= 0) {
                    numNodes = parent.numNodes();
                }
            }
            parentContext = parentContext.getParent().orElse(null);
        }

        if (connectorFactory == Default.class) {
            throw new IllegalStateException(
                    "No parent found with a non-default connectorFactory "
                            + "for @ClusterTest");
        }

        final var finalConnectorFactory = connectorFactory;
        final var finalNumNodes = numNodes;

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
        var type = parameterContext.getParameter().getType();
        if (!List.class.isAssignableFrom(type)) {
            return false;
        }
        var genericType = (ParameterizedType) parameterContext
                .getParameter()
                .getParameterizedType();
        var actualTypes = genericType.getActualTypeArguments();
        return actualTypes.length == 1 && actualTypes[0].equals(Grid.class);
    }
}
