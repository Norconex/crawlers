/* Copyright 2010-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.filter.impl;

import static com.norconex.commons.lang.config.Configurable.configure;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;

class SegmentCountUrlFilterTest {

    private SegmentCountUrlFilter f;
    private String url;

    @AfterEach
    void tearDown() throws Exception {
        f = null;
        url = null;
    }

    @Test
    void testSegmentMatches() {
        //--- Test proper segment counts ---
        url = "http://www.example.com/one/two/three/four/five/page.html";
        f = configure(
                new SegmentCountUrlFilter(), c -> c
                        .setCount(5)
                        .setOnMatch(OnMatch.EXCLUDE));
        Assertions.assertFalse(
                f.acceptReference(url),
                "URL wrongfully rejected.");
        f = configure(
                new SegmentCountUrlFilter(), c -> c
                        .setCount(6)
                        .setOnMatch(OnMatch.EXCLUDE));
        Assertions.assertFalse(
                f.acceptReference(url),
                "URL wrongfully accepted.");
        f = configure(
                new SegmentCountUrlFilter(), c -> c
                        .setCount(7)
                        .setOnMatch(OnMatch.EXCLUDE));
        Assertions.assertTrue(
                f.acceptReference(url),
                "URL wrongfully rejected.");

        //--- Test proper duplicate counts ---
        url = "http://www.example.com/aa/bb/aa/cc/bb/aa/aa/bb/cc/dd.html";
        f = configure(
                new SegmentCountUrlFilter(), c -> c
                        .setCount(3)
                        .setOnMatch(OnMatch.EXCLUDE)
                        .setDuplicate(true));
        Assertions.assertFalse(
                f.acceptReference(url),
                "URL wrongfully rejected.");
        f = configure(
                new SegmentCountUrlFilter(), c -> c
                        .setCount(4)
                        .setOnMatch(OnMatch.EXCLUDE)
                        .setDuplicate(true));
        Assertions.assertFalse(
                f.acceptReference(url),
                "URL wrongfully accepted.");
        f = configure(
                new SegmentCountUrlFilter(), c -> c
                        .setCount(5)
                        .setOnMatch(OnMatch.EXCLUDE)
                        .setDuplicate(true));
        Assertions.assertTrue(
                f.acceptReference(url),
                "URL wrongfully rejected.");

        //--- Test custom separator (query string ---
        url = "http://www.example.com/one/two_three|four-five/page.html";
        f = configure(
                new SegmentCountUrlFilter(), c -> c
                        .setCount(5)
                        .setOnMatch(OnMatch.EXCLUDE)
                        .setSeparator("[/_|-]"));
        Assertions.assertFalse(
                f.acceptReference(url),
                "URL wrongfully rejected.");
        f = configure(
                new SegmentCountUrlFilter(), c -> c
                        .setCount(6)
                        .setOnMatch(OnMatch.EXCLUDE)
                        .setSeparator("[/_|-]"));
        Assertions.assertFalse(
                f.acceptReference(url),
                "URL wrongfully accepted.");
        f = configure(
                new SegmentCountUrlFilter(), c -> c
                        .setCount(7)
                        .setOnMatch(OnMatch.EXCLUDE)
                        .setSeparator("[/_|-]"));
        Assertions.assertTrue(
                f.acceptReference(url),
                "URL wrongfully rejected.");
    }

    @Test
    void testWriteRead() {
        var f = new SegmentCountUrlFilter();
        f.getConfiguration()
                .setCount(5)
                .setDuplicate(true)
                .setOnMatch(OnMatch.EXCLUDE)
                .setSeparator("[/&]");
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(f));
    }
}
