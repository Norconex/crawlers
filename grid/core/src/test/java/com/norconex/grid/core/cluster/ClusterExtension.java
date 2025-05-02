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
package com.norconex.grid.core.cluster;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.cluster.WithCluster.Default;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridQueue;
import com.norconex.grid.core.storage.GridSet;
import com.norconex.grid.core.storage.GridStore;

public class ClusterExtension
        implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    public static final String MAP_STORE_PREFIX = "map_store_";
    public static final String SET_STORE_PREFIX = "set_store_";
    public static final String QUEUE_STORE_PREFIX = "queue_store_";

    private static final String TEMP_DIR = "tempDir";
    private static final String CLUSTER = "cluster";
    private static final String NUM_NODES = "numNodes";
    private static final String SINGLE_NODE = "singleNode";

    private static final AtomicInteger storeCounter = new AtomicInteger();

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

        //TODO destroy the store?
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
                || (GridStore.class.isAssignableFrom(type) && numNodes == 1)
                || (isGridList(parameterContext) && numNodes > 1);
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) {

        var numNodes = getStore(extensionContext).get(NUM_NODES, Integer.class);
        var store = getStore(extensionContext);
        var type = parameterContext.getParameter().getType();

        if (type.isAssignableFrom(Path.class)) {
            return store.get(TEMP_DIR);
        }
        // cluster
        if (type.isAssignableFrom(Cluster.class) && numNodes <= 0) {
            return store.get(CLUSTER, Cluster.class);
        }
        // Many nodes
        if (isGridList(parameterContext) && numNodes > 1) {
            return store.get(CLUSTER, Cluster.class).newNodes(numNodes);
        }
        // 1 node
        if (numNodes == 1) {
            // If 1 node.. store the node in case there is a request for
            // multiple stores so we don't create a new node each time
            var singleNode = store.getOrComputeIfAbsent(
                    SINGLE_NODE,
                    k -> store.get(CLUSTER, Cluster.class).oneNewNode(),
                    Grid.class);
            if (type.isAssignableFrom(Grid.class)) {
                return singleNode;
            }
            Class<?> cls = (Class<?>) getGenericType(parameterContext)
                    .orElse(String.class);
            var i = storeCounter.incrementAndGet();
            if (type.isAssignableFrom(GridMap.class)) {
                return singleNode.getStorage().getMap(MAP_STORE_PREFIX + i,
                        cls);
            }
            if (type.isAssignableFrom(GridSet.class)) {
                return singleNode.getStorage().getSet(SET_STORE_PREFIX + i);
            }
            if (type.isAssignableFrom(GridQueue.class)) {
                return singleNode.getStorage().getQueue(
                        QUEUE_STORE_PREFIX + i, cls);
            }
        }
        throw new ParameterResolutionException(
                "Unsupported parameter type or incompatible with "
                        + "requested number of nodes: "
                        + type);
    }

    private Optional<WithCluster>
            findClusterAnnotation(ExtensionContext context) {
        return context.getElement().flatMap(this::findGridAnnotationOnElement)
                .or(() -> context.getTestClass()
                        .flatMap(this::findGridAnnotationOnElement))
                .or(() -> findClusterAnnotationOnParent(context));
    }

    private Optional<WithCluster>
            findGridAnnotationOnElement(AnnotatedElement element) {
        var withCluster = element.getAnnotation(WithCluster.class);
        if (withCluster != null) {
            return Optional.of(withCluster);
        }

        for (var annotation : element.getAnnotations()) {
            var type = annotation.annotationType();
            var composed = type.getAnnotation(WithCluster.class);
            if (composed != null) {
                return Optional.of(composed);
            }
        }
        return Optional.empty();
    }

    private Optional<WithCluster>
            findClusterAnnotationOnParent(ExtensionContext context) {
        var current = context.getTestClass().orElse(null);
        while (current != null && current != Object.class) {
            for (var annotation : current.getAnnotations()) {
                var composed = annotation.annotationType()
                        .getAnnotation(WithCluster.class);
                if (composed != null) {
                    return Optional.of(composed);
                }
            }
            current = current.getSuperclass();
        }
        return Optional.empty();
    }

    private WithCluster
            resolveEffectiveClusterAnnotation(ExtensionContext context) {
        var annotation = findClusterAnnotation(context)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing @WithCluster annotation"));

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
                            + "for @WithCluster");
        }

        final var finalConnectorFactory = connectorFactory;
        final var finalNumNodes = numNodes;

        return new WithCluster() {
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
                return WithCluster.class;
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

    private Optional<Type> getGenericType(ParameterContext parameterContext) {
        var paramContextType = parameterContext
                .getParameter()
                .getParameterizedType();
        if (paramContextType instanceof ParameterizedType ptype) {
            var actualTypes = ptype.getActualTypeArguments();
            if (actualTypes.length > 0) {
                return Optional.ofNullable(actualTypes[0]);
            }
        }
        return Optional.empty();
    }
}
