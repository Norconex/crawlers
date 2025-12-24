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
package com.norconex.crawler.core.test;

import static java.util.Optional.ofNullable;

import java.io.Closeable;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.norconex.crawler.core.CrawlConfig;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

public interface CrawlTestHarness extends Closeable {

    //TODO have a builder launched by either standalone or clustered
    // then the builder is for instrumentalization (and modifying config).
    // then offer to launch, get config, and get crawl metrics.
    @Setter
    @Getter(value = AccessLevel.PACKAGE)
    @Accessors(fluent = true)
    @RequiredArgsConstructor
    public abstract static class Builder {
        private final CrawlConfig crawlConfig;
    }

    @Setter
    @Getter(value = AccessLevel.PACKAGE)
    @Accessors(fluent = true)
    public static class StandaloneBuilder extends Builder {
        /** Directory from which node-specific directories will be created.*/
        //private Path clusterDir;
        private boolean recordEvents;
        private boolean recordLogs;
        private boolean recordCaches;
        private Consumer<CrawlConfig> configModifier;

        public StandaloneBuilder(@NonNull CrawlConfig crawlConfig) {
            super(crawlConfig);
        }

        public CrawlTestHarness build() {
            return new StandaloneHarnessImpl(this);
        }
    }

    @Setter
    @Getter(value = AccessLevel.PACKAGE)
    @Accessors(fluent = true)
    public static class ClusteredBuilder extends Builder {
        public ClusteredBuilder(CrawlConfig crawlConfig) {
            super(crawlConfig);
        }

        public CrawlTestHarness build() {
            return new ClusteredHarnessImpl(this);
        }
    }

    static StandaloneBuilder standalone(Path workDir) {
        return standalone(workDir, null);
    }

    static StandaloneBuilder standalone(Path workDir, CrawlConfig crawlConfig) {
        var cfg = ofNullable(crawlConfig).orElseGet(CrawlConfig::new);
        cfg.setWorkDir(workDir);
        return new StandaloneBuilder(cfg);
    }

    static ClusteredBuilder clustered() {
        return clustered(null);
    }

    static ClusteredBuilder clustered(CrawlConfig crawlConfig) {
        return new ClusteredBuilder(crawlConfig);
    }

    /**
     * Gets the crawl configuration.
     * @return crawl configuration
     */
    CrawlConfig getCrawlConfig();

    /**
     * Launches a new crawler node for each node names specified and wait
     * for them to terminate before returning. The first
     * time this method is called for this instance will create a new cluster.
     * Subsequent times, it adds to the existing cluster.
     *
     * <b>Specifying one or several names for a standalone crawl has no
     * effect.</b>
     * @param nodeNames names of each nodes (must be unique)
     * @return the added nodes
     */
    CrawlTestResult launch(@NonNull String... nodeNames);

    CompletableFuture<CrawlTestResult>
            launchAsync(@NonNull String... nodeNames);

    /**
     * Get test results so far. If called after the crawl has terminated,
     * the results shall be the same as the test result returned by
     * the launch methods.
     * @return result
     */
    CrawlTestResult getResult();

    CrawlTestWaitFor waitFor();

    CrawlTestWaitFor waitFor(Duration timeout);

}
