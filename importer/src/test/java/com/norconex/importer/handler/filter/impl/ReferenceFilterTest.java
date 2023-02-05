/* Copyright 2020-2022 Norconex Inc.
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
package com.norconex.importer.handler.filter.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.parser.ParseState;

class ReferenceFilterTest {

    @Test
    void testAcceptDocument()
            throws ImporterHandlerException {
        var meta = new Properties();
        var filter = new ReferenceFilter();

        filter.setValueMatcher(TextMatcher.regex(".*/login.*"));
        filter.setOnMatch(OnMatch.EXCLUDE);

        Assertions.assertFalse(TestUtil.filter(filter,
                "http://www.example.com/login", null, meta, ParseState.PRE),
                "URL not filtered properly.");

        Assertions.assertTrue(TestUtil.filter(filter,
                "http://www.example.com/blah", null, meta, ParseState.PRE),
                "URL not filtered properly.");
    }

    @Test
    void testWriteRead() {
        var filter = new ReferenceFilter(TextMatcher.regex("blah"));
        filter.addRestriction(new PropertyMatcher(
                TextMatcher.basic("author"),
                TextMatcher.regex("Pascal.*").setIgnoreCase(true)));
        filter.setOnMatch(OnMatch.INCLUDE);
        XML.assertWriteRead(filter, "handler");
    }
}
