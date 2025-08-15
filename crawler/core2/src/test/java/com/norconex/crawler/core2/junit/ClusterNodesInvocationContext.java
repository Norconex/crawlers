package com.norconex.crawler.core2.junit;

import java.lang.reflect.ParameterizedType;
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

import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core2.CrawlConfig;
import com.norconex.crawler.core2.CrawlDriver;
import com.norconex.crawler.core2.cluster.CacheManager;
import com.norconex.crawler.core2.cluster.ClusterConnector;
import com.norconex.crawler.core2.cluster.TaskManager;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanClusterConnector;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanUtil;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.session.CrawlSessionFactory;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class ClusterNodesInvocationContext implements TestTemplateInvocationContext {

    private final int nodeCount;
    private final ClusterNodesTest annotation;

    @Override
    public String getDisplayName(int invocationIndex) {
        return "(nodes=" + nodeCount + ")";
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        List<CrawlSession> sessions = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            sessions.add(createSession(annotation));
        }
        // Wait for membership if requested
        if (annotation.waitForMembership()) {
            try {
                ClusterTestUtil.waitForClusterSize(
                        sessions,
                        nodeCount,
                        Duration.ofSeconds(annotation.membershipTimeoutSeconds()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for cluster membership", e);
            } catch (IllegalStateException e) {
                throw new RuntimeException("Cluster membership did not reach expected size " + nodeCount, e);
            }
        }
        return List.of(
                new SessionsParameterResolver(nodeCount, sessions),
                new SessionsCleanup(sessions));
    }

    private CrawlSession createSession(ClusterNodesTest ann) {
        try {
            @SuppressWarnings("unchecked")
            Supplier<CrawlDriver> driverFactory = (Supplier<CrawlDriver>) ann.driverFactory()
                    .getDeclaredConstructor().newInstance();
            var cfg = new CrawlConfig();
            cfg.setId("clusterNodesTest-" + System.nanoTime());
            ClusterConnector connector = ann.connector().getDeclaredConstructor().newInstance();
            if (connector instanceof InfinispanClusterConnector ic) {
                ic.getConfiguration().setInfinispan(
                        InfinispanUtil.configBuilderHolder(ann.infinispanConfig()));
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
        public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
            var t = pc.getParameter().getType();
            if (t == int.class || t == Integer.class) return true;
            if (List.class.isAssignableFrom(t)) {
                // Only support List<CrawlSession>
                if (pc.getParameter().getParameterizedType() instanceof ParameterizedType pt) {
                    return pt.getActualTypeArguments()[0].getTypeName()
                            .equals(CrawlSession.class.getName());
                }
                return false;
            }
            if (CrawlSession.class.isAssignableFrom(t)) return true;
            if (Cluster.class.isAssignableFrom(t)) return true;
            if (CacheManager.class.isAssignableFrom(t)) return true;
            if (TaskManager.class.isAssignableFrom(t)) return true;
            return false;
        }

        @Override
        public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
            var t = pc.getParameter().getType();
            if (t == int.class || t == Integer.class) return nodeCount;
            if (List.class.isAssignableFrom(t)) return sessions; // List<CrawlSession>
            var first = sessions.get(0);
            if (CrawlSession.class.isAssignableFrom(t)) return first;
            if (Cluster.class.isAssignableFrom(t)) return first.getCluster();
            if (CacheManager.class.isAssignableFrom(t)) return first.getCluster().getCacheManager();
            if (TaskManager.class.isAssignableFrom(t)) return first.getCluster().getTaskManager();
            throw new IllegalArgumentException("Unsupported parameter: " + pc.getParameter());
        }
    }

    @RequiredArgsConstructor
    static class SessionsCleanup implements AfterEachCallback {
        private final List<CrawlSession> sessions;
        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            for (var s : sessions) {
                try { s.close(); } catch (Exception e) { /* ignore */ }
            }
            sessions.clear();
        }
    }
}