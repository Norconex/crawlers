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
package com.norconex.crawler.core.stubs;

import java.nio.file.Path;
import java.util.function.Consumer;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionFactory;

public final class CrawlSessionStubber {
    private CrawlSessionStubber() {
    }

    public static CrawlSession multiNodesCrawlSession(Path workDir) {
        return multiNodesCrawlSession(workDir, null);
    }

    public static CrawlSession multiNodesCrawlSession(
            Path workDir, Consumer<CrawlConfig> configModifier) {
        var config = CrawlerConfigStubber.memoryCrawlerConfig(workDir);
        config.setClusterConnector(
                ClusterStubber.multiMemoryNodesClusterConnector());
        if (configModifier != null) {
            configModifier.accept(config);
        }
        return CrawlSessionFactory.create(
                TestCrawlDriverFactory.create(),
                config);
    }

    //TODO if the multiNodes version works as well and tests are as fast
    // then delete this one and corredponding config:
    public static CrawlSession singleNodeCrawlSession(Path workDir) {
        var config = CrawlerConfigStubber.memoryCrawlerConfig(workDir);
        config.setClusterConnector(
                ClusterStubber.singleMemoryNodeClusterConnector());
        return CrawlSessionFactory.create(
                TestCrawlDriverFactory.create(),
                config);
    }
}
