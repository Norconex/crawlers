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
package com.norconex.crawler.core.session;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;

import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.ObjectCreationException;
import org.jeasy.random.api.ObjectFactory;
import org.jeasy.random.api.RandomizerContext;
import org.jeasy.random.randomizers.number.LongRandomizer;
import org.jeasy.random.randomizers.text.StringRandomizer;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.crawler.CrawlerConfig;

class CrawlSessionConfigTest {

    private EasyRandom easyRandom = new EasyRandom(new EasyRandomParameters()
            .seed(System.currentTimeMillis())
            .collectionSizeRange(1, 5)
            .randomizationDepth(5)
            .scanClasspathForConcreteTypes(true)
            .overrideDefaultInitialization(true)
            .randomize(Path.class,
                    () -> Path.of(new StringRandomizer(100).getRandomValue()))
            .randomize(Long.class,
                    () -> Math.abs(new LongRandomizer().getRandomValue()))
            .objectFactory(new ObjectFactory() {
                @SuppressWarnings("unchecked")
                @Override
                public <T> T createInstance(
                        Class<T> type, RandomizerContext context)
                                throws ObjectCreationException {
                    if (type.isAssignableFrom(CrawlSessionConfig.class)) {
                        return (T) new CrawlSessionConfig(CrawlerConfig.class);
                    }
                    try {
                        return type.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        throw new ObjectCreationException(
                                "Unable to create a new instance of " + type,
                                e);
                    }
                }
            })
    );

    @Test
    void testCrawlSessionConfig() {
        var cfg =
                easyRandom.nextObject(CrawlSessionConfig.class);
        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(cfg, "config"));
    }

    @Test
    void testOverwriteCrawlerDefaults() throws IOException {
        var cfg = new CrawlSessionConfig();
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
                "overwrite-crawlerDefaults.xml"))) {
            XML.of(r).create().populate(cfg);
        }

        var crawlA = cfg.getCrawlerConfigs().get(0);
//TODO when CrawlerConfig has been migrated
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
//        MockCrawlerConfig crawlB =
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
    }
}
