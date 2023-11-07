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

class CrawlSessionConfigTest {
    //TODO migrate these:
    /*

    @Test
    void testCrawlSessionConfig() {
        var cfg = CoreStubber.randomize(CrawlSessionConfig.class);
        assertThatNoException().isThrownBy(
                () -> TestUtil.beanMapper().assertWriteRead(cfg));
    }

    @Test
    void testOverwriteCrawlerDefaults() throws IOException {
        var cfg = new CrawlSessionConfig();
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
                "overwrite-crawlerDefaults.xml"))) {
            CrawlSessionConfigMapperFactory.create(CrawlerConfig.class)
                    .read(cfg, r, Format.XML);
//            XML.of(r).create().populate(cfg);
        }

        var crawlA = cfg.getCrawlerConfigs().get(0);
        assertEquals(22, crawlA.getNumThreads(),
                "crawlA");
        assertEquals("crawlAFilter", ((ExtensionReferenceFilter)
                crawlA.getReferenceFilters().get(0)).getConfiguration()
                        .getExtensions().iterator().next(), "crawlA");
//        assertEquals("F", ((ReplaceTransformer)
//                ((HandlerConsumerAdapter) crawlA.getImporterConfig()
//                        .getPreParseConsumer()).getHandler())
//                        .getReplacements().get(0).getToValue(),
//                "crawlA");
        assertTrue(CollectionUtils.isEmpty(
                (List<?>) crawlA.getImporterConfig().getHandler()),
                "crawlA");
        assertEquals("crawlACommitter", ((JSONFileCommitter)
                crawlA.getCommitters().get(0))
                    .getConfiguration()
                    .getDirectory()
                    .toString(),
                "crawlA");

        var crawlB = cfg.getCrawlerConfigs().get(1);
        assertEquals(1, crawlB.getNumThreads(), "crawlB");
        assertEquals("defaultFilter", ((ExtensionReferenceFilter)
                crawlB.getReferenceFilters().get(0))
                    .getConfiguration()
                    .getExtensions()
                        .iterator().next(), "crawlB");
//        assertEquals("B", ((ReplaceTransformer)
//                ((HandlerConsumerAdapter) crawlB.getImporterConfig()
//                        .getPreParseConsumer()).getHandler())
//                        .getReplacements().get(0).getToValue(),
//                "crawlB");
//        assertEquals("D", ((ReplaceTransformer)
//                ((HandlerConsumerAdapter) crawlB.getImporterConfig()
//                        .getPostParseConsumer()).getHandler())
//                        .getReplacements().get(0).getToValue(),
//                "crawlB");
        assertEquals("defaultCommitter", ((JSONFileCommitter)
                crawlB.getCommitters().get(0))
                    .getConfiguration()
                    .getDirectory()
                    .toString(),
                "crawlB");
    }
    */
}
