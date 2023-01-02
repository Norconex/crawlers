/* Copyright 2016-2022 Norconex Inc.
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
package com.norconex.crawler.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.committer.core.fs.impl.JSONFileCommitter;
import com.norconex.commons.lang.xml.ErrorHandlerCapturer;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.crawler.MockCrawlerConfig;

/**
 * @since 1.7.0
 */
class CollectorTest {

    @Test
    void testWriteRead() {
        var config = new MockCollectorConfig();
        config.setId("test-collector");
        config.setMaxConcurrentCrawlers(100);
        config.setEventListeners(List.of(new MockCollectorEventListener()));

        var crawlerCfg = new MockCrawlerConfig();
        crawlerCfg.setId("myCrawler");
        crawlerCfg.setCommitters(List.of(new JSONFileCommitter()));

        config.setCrawlerConfigs(List.of(crawlerCfg));

        XML.assertWriteRead(config, "collector");
    }

//    @SuppressWarnings("deprecation")
//    @Test
//    void testOverwriteCrawlerDefaults() throws IOException {
//        var cfg = new MockCollectorConfig();
//        try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
//                "overwrite-crawlerDefaults.xml"))) {
//            XML.of(r).create().populate(cfg);
//        }
//
//        var crawlA =
//                (MockCrawlerConfig) cfg.getCrawlerConfigs().get(0);
//        assertEquals(22, crawlA.getNumThreads(),
//                "crawlA");
//        assertEquals("crawlAFilter", ((ExtensionReferenceFilter)
//                crawlA.getReferenceFilters().get(0))
//                        .getExtensions().iterator().next(), "crawlA");
//        assertEquals("F", ((ReplaceTransformer)
//                crawlA.getImporterConfig().getPreParseHandlers().get(0))
//                        .getReplacements().get(0).getToValue(),
//                "crawlA");
//        assertTrue(CollectionUtils.isEmpty(
//                crawlA.getImporterConfig().getPostParseHandlers()),
//                "crawlA");
//        assertEquals("crawlACommitter", ((JSONFileCommitter)
//                crawlA.getCommitters().get(0)).getDirectory().toString(),
//                "crawlA");
//
//        var crawlB =
//                (MockCrawlerConfig) cfg.getCrawlerConfigs().get(1);
//        assertEquals(1, crawlB.getNumThreads(), "crawlB");
//        assertEquals("defaultFilter", ((ExtensionReferenceFilter)
//                crawlB.getReferenceFilters().get(0)).getExtensions()
//                        .iterator().next(), "crawlB");
//        assertEquals("B", ((ReplaceTransformer)
//                crawlB.getImporterConfig().getPreParseHandlers().get(0))
//                        .getReplacements().get(0).getToValue(),
//                "crawlB");
//        assertEquals("D", ((ReplaceTransformer)
//                crawlB.getImporterConfig().getPostParseHandlers().get(0))
//                        .getReplacements().get(0).getToValue(),
//                "crawlB");
//        assertEquals("defaultCommitter", ((JSONFileCommitter)
//                crawlB.getCommitters().get(0)).getDirectory().toString(),
//                "crawlB");
//    }


    @Test
    void testValidation() throws IOException {
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
                "/validation/collector-core-full.xml"))) {
            var eh = new ErrorHandlerCapturer();
            XML.of(r).setErrorHandler(eh).create().populate(
                    new MockCollectorConfig());
            assertEquals(0, eh.getErrors().size(),
                "Validation warnings/errors were found: " + eh.getErrors());
        }
    }
}
