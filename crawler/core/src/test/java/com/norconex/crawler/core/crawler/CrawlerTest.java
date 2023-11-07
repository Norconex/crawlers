/* Copyright 2022-2022 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

class CrawlerTest {

    @TempDir
    private Path tempDir;

    //TODO Migrate this:

    /*
    @Test
    void testLifeCycle() {

        var crawlSession = CoreStubber.crawlSession(tempDir,
                "mock:ref1", "mock:ref2", "mock:ref3", "mock:ref4");

        // Start
        crawlSession.start();
        // All 4 docs must be committed and not be found in cache
        assertThat(TestUtil
                .getFirstMemoryCommitter(crawlSession)
                .getAllRequests())
            .allMatch(req -> req.getReference().startsWith("mock:ref"))
            .allMatch(req -> !req.getMetadata().getBoolean("mock.alsoCached"))
            .hasSize(4);

        // Export
        var exportDir = tempDir.resolve("exportdir");
        var exportFile = exportDir.resolve("test-crawler.zip");
        crawlSession.exportDataStore(exportDir);

        // Clean
        crawlSession.clean();

        // Import
        crawlSession.importDataStore(List.of(exportFile));

        // New session with 1 new 2 modified, and 1 orphan
        crawlSession = CoreStubber.crawlSession(tempDir,
                "mock:ref2", "mock:ref3", "mock:ref4", "mock:ref5");

        // Start
        crawlSession.start();
        // 5 docs must be committed:
        //    1 new
        //    3 modified (also cached)
        //    1 orphan (also cached)
        assertThat(TestUtil
                .getFirstMemoryCommitter(crawlSession)
                .getAllRequests())
            .allMatch(req -> req.getReference().startsWith("mock:ref"))
            .hasSize(5)
            .areExactly(4, new Condition<>(req ->
                    req.getMetadata().getBoolean("mock.alsoCached"), ""))
            .areExactly(1, new Condition<>(req -> req.getMetadata().getBoolean(
                    "collector.is-crawl-new"), ""))
            .map(CommitterRequest::getReference)
            // ref1 is last because orphans are processed last
            .containsExactly("mock:ref2", "mock:ref3", "mock:ref4", "mock:ref5",
                    "mock:ref1");
    }

    @Test
    void testErrors() {
        var sess = CoreStubber.crawlSession(tempDir);
        TestUtil.getFirstCrawlerConfig(sess).setId(null);
        assertThatExceptionOfType(CrawlerException.class).isThrownBy(() ->
            sess.start());
    }

    @Test
    void testOrphanDeletion() {

        var crawlSession = CoreStubber.crawlSession(tempDir,
                "mock:ref1", "mock:ref2", "mock:ref3", "mock:ref4");
        crawlSession.start();

        // New session with 1 new 2 modified, and 1 orphan
        crawlSession = CoreStubber.crawlSession(tempDir,
                "mock:ref2", "mock:ref3", "mock:ref4", "mock:ref5");
        TestUtil.getFirstCrawlerConfig(
                crawlSession).setOrphansStrategy(OrphansStrategy.DELETE);
        crawlSession.start();

        var mem = TestUtil.getFirstMemoryCommitter(crawlSession);

        assertThat(mem.getAllRequests())
            .allMatch(req -> req.getReference().startsWith("mock:ref"))
            .hasSize(5);

        assertThat(mem.getUpsertCount()).isEqualTo(4);
        assertThat(mem.getDeleteCount()).isEqualTo(1);
        assertThat(mem.getDeleteRequests().get(0).getReference())
            .isEqualTo("mock:ref1");
    }
    */
}
