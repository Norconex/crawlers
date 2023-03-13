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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;

import com.norconex.committer.core.fs.impl.JSONFileCommitter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.CoreStubber;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.filter.impl.ExtensionReferenceFilter;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.transformer.impl.ReplaceTransformer;

class CrawlSessionConfigTest {

    @Test
    void testCrawlSessionConfig() {
        var cfg = CoreStubber.randomize(CrawlSessionConfig.class);
        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(cfg, "crawlSession"));
    }

    @Test
    void testOverwriteCrawlerDefaults() throws IOException {
        var cfg = new CrawlSessionConfig(CrawlerConfig.class);
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
                "overwrite-crawlerDefaults.xml"))) {
            XML.of(r).create().populate(cfg);
        }

        var crawlA = cfg.getCrawlerConfigs().get(0);
        assertEquals(22, crawlA.getNumThreads(),
                "crawlA");
        assertEquals("crawlAFilter", ((ExtensionReferenceFilter)
                crawlA.getReferenceFilters().get(0))
                        .getExtensions().iterator().next(), "crawlA");
        assertEquals("F", ((ReplaceTransformer)
                ((HandlerConsumer) crawlA.getImporterConfig()
                        .getPreParseConsumer()).getHandler())
                        .getReplacements().get(0).getToValue(),
                "crawlA");
        assertTrue(CollectionUtils.isEmpty(
                (List<?>) crawlA.getImporterConfig().getPostParseConsumer()),
                "crawlA");
        assertEquals("crawlACommitter", ((JSONFileCommitter)
                crawlA.getCommitters().get(0)).getDirectory().toString(),
                "crawlA");

        var crawlB = cfg.getCrawlerConfigs().get(1);
        assertEquals(1, crawlB.getNumThreads(), "crawlB");
        assertEquals("defaultFilter", ((ExtensionReferenceFilter)
                crawlB.getReferenceFilters().get(0)).getExtensions()
                        .iterator().next(), "crawlB");
        assertEquals("B", ((ReplaceTransformer)
                ((HandlerConsumer) crawlB.getImporterConfig()
                        .getPreParseConsumer()).getHandler())
                        .getReplacements().get(0).getToValue(),
                "crawlB");
        assertEquals("D", ((ReplaceTransformer)
                ((HandlerConsumer) crawlB.getImporterConfig()
                        .getPostParseConsumer()).getHandler())
                        .getReplacements().get(0).getToValue(),
                "crawlB");
        assertEquals("defaultCommitter", ((JSONFileCommitter)
                crawlB.getCommitters().get(0)).getDirectory().toString(),
                "crawlB");
    }
}
