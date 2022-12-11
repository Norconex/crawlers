/* Copyright 2022 Norconex Inc.
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

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;

class CrawlSessionTest {

//    @Test
//    void testBuilder() {
//        throw new RuntimeException("not yet implemented");
//    }
//
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
                .crawlerFactory(() -> Crawler.builder().build())
                .build();

        ses.stop();

        assertThat(ses.getId()).isEqualTo("sessionId");
        assertThat(CrawlSession.get()).isSameAs(ses);
        assertThat(ses.getWorkDir()).isEqualTo(Paths.get("/tmp/sessionId"));

//        assertThat(ses.get).isEqualTo("sessionId");
    }

//    @Test
//    void testGet() {
//        throw new RuntimeException("not yet implemented");
//    }
//
//    @Test
//    void testGetWorkDir() {
//        throw new RuntimeException("not yet implemented");
//    }
//
//    @Test
//    void testDestroyCollector() {
//        throw new RuntimeException("not yet implemented");
//    }
//
//    @Test
//    void testStop() {
//        throw new RuntimeException("not yet implemented");
//    }
//
//    @Test
//    void testGetId() {
//        throw new RuntimeException("not yet implemented");
//    }
//
//    @Test
//    void testGetCrawlers() {
//        throw new RuntimeException("not yet implemented");
//    }
//
//    @Test
//    void testUnlock() {
//        throw new RuntimeException("not yet implemented");
//    }
//
//    @Test
//    void testIsRunning() {
//        throw new RuntimeException("not yet implemented");
//    }

}
