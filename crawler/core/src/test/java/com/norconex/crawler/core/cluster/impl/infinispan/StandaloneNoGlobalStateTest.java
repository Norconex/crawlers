package com.norconex.crawler.core.cluster.impl.infinispan;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.cluster.Cluster;

/**
 * Sanity test for the STANDALONE_MEMORY preset: verifies that the
 * InfinispanCluster can be created and started using the lightweight
 * configuration without throwing exceptions.
 */
class StandaloneMemoryPresetTest {

    @TempDir
    private Path tempDir;

    @Test
    @Timeout(30)
    void testStandaloneMemoryPresetBoots() {
        var workDir = tempDir.resolve("work");

        var cfg = new InfinispanClusterConfig()
                .setPreset(InfinispanClusterConfig.Preset.STANDALONE_MEMORY);

        assertThatNoException()
                .as("InfinispanCluster with STANDALONE_MEMORY"
                        + " should initialize without error")
                .isThrownBy(() -> {
                    try (Cluster cluster = new InfinispanCluster(cfg)) {
                        cluster.init(workDir);
                    }
                });
    }
}
