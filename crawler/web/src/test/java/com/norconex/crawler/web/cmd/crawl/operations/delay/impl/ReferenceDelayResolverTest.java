/* Copyright 2016-2024 Norconex Inc.
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
package com.norconex.crawler.web.cmd.crawl.operations.delay.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.xml.Xml;
import com.norconex.crawler.web.cmd.crawl.operations.delay.impl.BaseDelayResolverConfig.DelayResolverScope;

class ReferenceDelayResolverTest {

    @Test
    void testWriteRead() {
        List<DelayReferencePattern> delayPatterns = new ArrayList<>();
        delayPatterns.add(
                new DelayReferencePattern(
                        "http://example\\.com/.*", Duration.ofSeconds(1)));

        var r = new ReferenceDelayResolver();
        r.getConfiguration()
                .setDelayReferencePatterns(delayPatterns)
                .setDefaultDelay(Duration.ofSeconds(10))
                .setIgnoreRobotsCrawlDelay(true)
                .setScope(DelayResolverScope.THREAD);

        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(r));
    }

    @Test
    void testValidate() {
        assertThatNoException().isThrownBy(
                () -> new Xml(ResourceLoader.getXmlReader(getClass()))
                        .validate(ReferenceDelayResolver.class));
    }

    @Test
    void testResolveExplicitDelay() {
        var r = new ReferenceDelayResolver();
        r.getConfiguration().setDelayReferencePatterns(
                List.of(
                        new DelayReferencePattern(
                                ".*abc.*", Duration.ofMillis(123)),
                        new DelayReferencePattern(
                                ".*def.*", Duration.ofMillis(456))));

        assertThat(r.resolveExplicitDelay("http://abc.com")).isEqualTo(
                Duration.ofMillis(123));
        assertThat(r.resolveExplicitDelay("http://def.com")).isEqualTo(
                Duration.ofMillis(456));
        assertThat(r.resolveExplicitDelay("http://ghi.com")).isNull();
    }
}
