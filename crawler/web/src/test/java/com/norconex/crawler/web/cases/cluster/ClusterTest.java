/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.crawler.web.cases.cluster;

import static com.norconex.crawler.web.WebsiteMock.serverUrl;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.committer.core.impl.LogCommitter;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.crawler.web.doc.operations.delay.impl.GenericDelayResolver;

/**
 * Test that MaxDepth setting is respected.
 */
@MockServerSettings
class ClusterTest {

    @TempDir
    private Path tempDir;

    @Test
    void testMaxDepth(ClientAndServer client) throws IOException {
        WebsiteMock.whenInfiniteDepth(client);

        WebTestUtil.runWithConfig(tempDir, cfg -> {
            cfg.setStartReferences(
                    List.of(serverUrl(client, "/clusterTest/0000")));
            cfg.setDelayResolver(
                    Configurable.configure(new GenericDelayResolver(),
                            c -> c.setDefaultDelay(Duration.ofSeconds(3))));
            //            cfg.setDataStoreEngine(new IgniteDataStoreEngine());
            cfg.setMaxDepth(10);
            cfg.setCommitters(
                    List.of(cfg.getCommitters().get(0), new LogCommitter()));
        });

        // 0-depth + 10 others == 11 expected files
        //  assertThat(mem.getRequestCount()).isEqualTo(11);
    }
}