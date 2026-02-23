package com.norconex.crawler.core.junit.cluster;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.norconex.crawler.core.CrawlConfig;

public class ClusteredExtension
        implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final String KEY_TEMP_DIR = "tempDir";
    private static final String KEY_CLUSTER_CLIENT = "clusterClient";

    private ClusterClient clusterClient;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // Create temp dir
        var tempDir = Files.createTempDirectory("junit-temp-");
        context.getStore(ExtensionContext.Namespace.GLOBAL)
                .put(KEY_TEMP_DIR, tempDir);

        clusterClient = new ClusterClient(
                new CrawlConfig()
                        .setWorkDir(tempDir));
        context.getStore(ExtensionContext.Namespace.GLOBAL).put(
                KEY_CLUSTER_CLIENT, clusterClient);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        var state = (ClusterClient) context
                .getStore(ExtensionContext.Namespace.GLOBAL)
                .remove(KEY_CLUSTER_CLIENT);
        if (state != null) {
            state.close();
        }

        // Delete temp dir
        var tempDir = (Path) context.getStore(ExtensionContext.Namespace.GLOBAL)
                .remove(KEY_TEMP_DIR);
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> p.toFile().delete());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
        return pc.getParameter().getType().equals(ClusterClient.class);
    }

    @Override
    public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
        if (pc.getParameter().getType().equals(ClusterClient.class)) {
            return ec.getStore(ExtensionContext.Namespace.GLOBAL)
                    .get(KEY_CLUSTER_CLIENT);
        }
        return null;
    }
}
