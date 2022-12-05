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
package com.norconex.crawler.core.crawler;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.randomizers.number.LongRandomizer;
import org.jeasy.random.randomizers.text.StringRandomizer;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.xml.XML;

class CrawlerConfigTest {

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
    );

    @Test
    void testCrawlerConfig() {
        CrawlerConfig cfg = easyRandom.nextObject(CrawlerConfig.class);
        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(cfg, "crawler"));

    }
}
