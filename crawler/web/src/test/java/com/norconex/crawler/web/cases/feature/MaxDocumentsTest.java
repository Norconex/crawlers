/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.web.cases.feature;

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.WebsiteMock;

/**
 * Test that MaxDocuments setting is respected.
 */
@MockServerSettings
class MaxDocumentsTest {

    @TempDir
    private Path tempDir;

    @Test
    void testMaxDocuments(ClientAndServer client) throws IOException {
        WebsiteMock.whenInfiniteDepth(client);

        var mem = WebTestUtil.runWithConfig(tempDir, cfg -> {
            cfg.setStartReferences(
                    List.of(serverUrl(client, "/maxDocuments/0000")));
            cfg.setMaxDocuments(15);
        });

        assertThat(mem.getRequestCount()).isEqualTo(15);
    }
}
