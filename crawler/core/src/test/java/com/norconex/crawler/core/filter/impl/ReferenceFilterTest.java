/* Copyright 2021-2022 Norconex Inc.
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
package com.norconex.crawler.core.filter.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.filter.OnMatch;

class ReferenceFilterTest {

    @Test
    void testCaseSensitivity() {
        ReferenceFilter f = new ReferenceFilter();
        f.setOnMatch(OnMatch.INCLUDE);

        // must match any case:
        f.setValueMatcher(TextMatcher.regex("case").setIgnoreCase(true));
        assertTrue(f.acceptReference("case"));
        assertTrue(f.acceptReference("CASE"));

        // must match only matching case:
        f.setValueMatcher(TextMatcher.regex("case").setIgnoreCase(false));
        assertTrue(f.acceptReference("case"));
        assertFalse(f.acceptReference("CASE"));
    }


    @Test
    void testWriteRead() {
        ReferenceFilter f = new ReferenceFilter();
        f.setValueMatcher(TextMatcher.regex(".*blah.*"));
        f.setOnMatch(OnMatch.EXCLUDE);
        XML.assertWriteRead(f, "filter");
    }
}
