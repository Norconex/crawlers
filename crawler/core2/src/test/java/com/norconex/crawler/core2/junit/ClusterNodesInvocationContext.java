package com.norconex.crawler.core2.junit;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.ClusterConnector;
import com.norconex.crawler.core.cluster.impl.infinispan.InfinispanClusterConnector;
import com.norconex.crawler.core.cluster.impl.infinispan.InfinispanUtil;
import com.norconex.crawler.core.cluster.pipeline.PipelineManager;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.session.CrawlSessionFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class ClusterNodesInvocationContext implements TestTemplateInvocationContext {

    private final int nodeCount;
    private final ClusterNodesTest annotation;
    private String sharedCrawlerId; // ensures same crawlerId across sessions
    private Path tempRootDir; // root temp dir for this invocation (one per test)

    @Override
    public String getDisplayName(int invocationIndex) {
        return "(nodeCount=" + nodeCount + ")";
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        if (sharedCrawlerId == null) {
            sharedCrawlerId = "clusterNodesTest-" + System.nanoTime();
        }
        if (tempRootDir == null) {
            try {
                tempRootDir =
                        Files.createTempDirectory(sharedCrawlerId + "-work");
            } catch (IOException e) {
                throw new RuntimeException(
                        "Could not create temporary work directory", e);
            }
        }
        List<CrawlSession> sessions = new ArrayList<>();
        for (var i = 0; i < nodeCount; i++) {
            var nodeDir = tempRootDir.resolve("node-" + i);
            try {
                Files.createDirectories(nodeDir);
            } catch (IOException e) {
                throw new RuntimeException(
                        "Could not create node work directory: " + nodeDir, e);
            }
            sessions.add(createSession(annotation, nodeDir));
        }
        // Wait for membership if requested
        if (annotation.waitForMembership()) {
            try {
                ClusterTestUtil.waitForClusterSize(
                        sessions,
                        nodeCount,
                        Duration.ofSeconds(
                                annotation.membershipTimeoutSeconds()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        "Interrupted while waiting for cluster membership", e);
            } catch (IllegalStateException e) {
                throw new RuntimeException(
                        "Cluster membership did not reach expected size "
                                + nodeCount,
                        e);
            }
        }
        return List.of(
                new SessionsParameterResolver(nodeCount, sessions),
                new SessionsCleanup(sessions, tempRootDir));
    }

    private CrawlSession createSession(ClusterNodesTest ann, Path workDir) {
        try {
            if (sharedCrawlerId == null) {
                sharedCrawlerId = "clusterNodesTest-" + System.nanoTime();
            }
            Supplier<CrawlDriver> driverFactory = ann.driverFactory()
                    .getDeclaredConstructor().newInstance();
            var cfg = new CrawlConfig();
            cfg.setId(sharedCrawlerId); // unified id across nodes
            cfg.setWorkDir(workDir);
            ClusterConnector connector =
                    ann.connector().getDeclaredConstructor().newInstance();
            if (connector instanceof InfinispanClusterConnector ic) {
                var builderHolder = InfinispanUtil.configBuilderHolder(
                        ann.infinispanConfig());
                ic.getConfiguration()
                        .setInfinispan(builderHolder)
                        .setNodeExpiryTimeout(Duration
                                .ofMillis(ann.infinispanNodeExpiryTimeout()));
            }
            cfg.setClusterConnector(connector);
            return CrawlSessionFactory.create(driverFactory.get(), cfg);
        } catch (Exception e) {
            throw new RuntimeException("Could not create session", e);
        }
    }

    // --- Nested extensions ---
    @RequiredArgsConstructor
    static class SessionsParameterResolver implements ParameterResolver {
        private final int nodeCount;
        private final List<CrawlSession> sessions;

        @Override
        public boolean supportsParameter(ParameterContext pc,
                ExtensionContext ec) {
            var t = pc.getParameter().getType();
            if (t == int.class || t == Integer.class)
                return true;
            if (List.class.isAssignableFrom(t)) {
                // Only support List<CrawlSession>
                if (pc.getParameter()
                        .getParameterizedType() instanceof ParameterizedType pt) {
                    return pt.getActualTypeArguments()[0].getTypeName()
                            .equals(CrawlSession.class.getName());
                }
                return false;
            }
            if (CrawlSession.class.isAssignableFrom(t)
                    || Cluster.class.isAssignableFrom(t)
                    || CacheManager.class.isAssignableFrom(t)
                    || PipelineManager.class.isAssignableFrom(t))
                return true;
            return false;
        }

        @Override
        public Object resolveParameter(ParameterContext pc,
                ExtensionContext ec) {
            var t = pc.getParameter().getType();
            if (t == int.class || t == Integer.class)
                return nodeCount;
            if (List.class.isAssignableFrom(t))
                return sessions; // List<CrawlSession>
            var first = sessions.get(0);
            if (CrawlSession.class.isAssignableFrom(t))
                return first;
            if (Cluster.class.isAssignableFrom(t))
                return first.getCluster();
            if (CacheManager.class.isAssignableFrom(t))
                return first.getCluster().getCacheManager();
            if (PipelineManager.class.isAssignableFrom(t))
                return first.getCluster().getPipelineManager();
            throw new IllegalArgumentException(
                    "Unsupported parameter: " + pc.getParameter());
        }
    }

    @RequiredArgsConstructor
    static class SessionsCleanup implements AfterEachCallback {
        private final List<CrawlSession> sessions;
        private final Path tempRootDir; // may be null

        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            for (var s : sessions) {
                try {
                    s.close();
                } catch (Exception e) {
                    /* ignore */ }
            }
            sessions.clear();
            if (tempRootDir != null) {
                try {
                    // Recursively delete temp directory
                    Files.walk(tempRootDir)
                            // delete children first
                            .sorted((a, b) -> b.compareTo(a))
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException e) {
                                    /* ignore */
                                }
                            });
                } catch (IOException e) {
                    // ignore cleanup failures
                }
            }
        }
    }
}
