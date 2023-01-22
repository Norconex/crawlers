/* Copyright 2022-2023 Norconex Inc.
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
package com.norconex.crawler.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;

class CrawlSessionTest {

    @TempDir
    private Path tempDir;

    @Test
    void testCrawlSession() {
        var sesCfg = new CrawlSessionConfig();
        sesCfg.setId("sessionId");
        sesCfg.setWorkDir(Path.of("/tmp"));

        var cc = new CrawlerConfig();
        cc.setId("crawlerId");

        sesCfg.setCrawlerConfigs(List.of(cc));


        var ses = CrawlSession.builder()
                .crawlSessionConfig(sesCfg)
                .crawlerFactory((session, cfg) -> Crawler.builder()
                        .crawlSession(session)
                        .crawlerConfig(cfg)
                        .build())
                .build();

        ses.stop();

        assertThat(ses.getId()).isEqualTo("sessionId");
        assertThat(CrawlSession.get()).isSameAs(ses);
        assertThat(ses.getWorkDir()).isEqualTo(Paths.get("/tmp/sessionId"));
    }
//
//    @Test
//    void testLifeCycle() throws IOException {
//
//        var crawlSession = Stubber.crawlSession(tempDir,
//                "mock:ref1", "mock:ref2", "mock:ref3", "mock:ref4");
//
//        // Start
//        crawlSession.start();
//        // All 4 docs must be committed and not be found in cache
//        assertThat(TestUtil
//                .getFirstMemoryCommitter(crawlSession)
//                .getAllRequests())
//            .allMatch(req -> req.getReference().startsWith("mock:ref"))
//            .allMatch(req -> !req.getMetadata().getBoolean("mock.alsoCached"))
//            .hasSize(4);
//
//        // Export
//        var exportDir = tempDir.resolve("exportdir");
//        var exportFile = exportDir.resolve("test-crawler.zip");
//        crawlSession.exportDataStore(exportDir);
//
//        // Clean
//        crawlSession.clean();
//
//        // Import
//        crawlSession.importDataStore(List.of(exportFile));
//
//        // New session with 1 new 2 modified, and 1 orphan
//        crawlSession = Stubber.crawlSession(tempDir,
//                "mock:ref2", "mock:ref3", "mock:ref4", "mock:ref5");
//
//        // Start
//        crawlSession.start();
//        // 5 docs must be committed:
//        //    1 new
//        //    3 modified (also cached)
//        //    1 orphan (also cached)
//        assertThat(TestUtil
//                .getFirstMemoryCommitter(crawlSession)
//                .getAllRequests())
//            .allMatch(req -> req.getReference().startsWith("mock:ref"))
//            .hasSize(5)
//            .areExactly(4, new Condition<>(req ->
//                    req.getMetadata().getBoolean("mock.alsoCached"), ""))
//            .areExactly(1, new Condition<>(req -> req.getMetadata().getBoolean(
//                    "collector.is-crawl-new"), ""))
//            .map(CommitterRequest::getReference)
//            // ref1 is last because orphans are processed last
//            .containsExactly("mock:ref2", "mock:ref3", "mock:ref4", "mock:ref5",
//                    "mock:ref1");
//
//        // The following must be set:
//        var crawler = TestUtil.getFirstCrawler(crawlSession);
//        assertThat(crawler.getQueuePipeline()).isNotNull();
//        assertThat(crawler.getImporterPipeline()).isNotNull();
//        assertThat(crawler.getCommitterPipeline()).isNotNull();
//    }
}
