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
package com.norconex.crawler.core.filter.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.filter.OnMatch;

class ExtensionReferenceFilterTest {

    @Test
    void testOnlyDetectExtensionsInLastPathSegment() {
        ExtensionReferenceFilter filter = initFilter("com", "xml");

        Assertions.assertFalse(
                filter.acceptReference("http://example.com"));

        Assertions.assertFalse(
                filter.acceptReference("http://example.com/file"));

        Assertions.assertFalse(
                filter.acceptReference("http://example.com/dir.com/file"));

        Assertions.assertTrue(
                filter.acceptReference("http://example.com/file.com"));

        Assertions.assertTrue(
                filter.acceptReference("http://example.de/file.com"));

        Assertions.assertTrue(
                filter.acceptReference("http://example.com/file.xml"));

        Assertions.assertTrue(
                filter.acceptReference("http://example.com/file.subtype.xml"));

        Assertions.assertTrue(
                filter.acceptReference("http://example.com/dir.com/file.com"));

        Assertions.assertFalse(
                filter.acceptReference("http://example.com/dir.com/file.pdf"));

        Assertions.assertTrue(
                filter.acceptReference("http://example.com/dir.pdf/file.com"));

        Assertions.assertTrue(filter.acceptReference(
                "http://example.com/dir.pdf/file.com?param1=something.pdf"));

        Assertions.assertFalse(filter.acceptReference(
                "http://example.com/dir.pdf/file.pdf?param1=something.com"));

        Assertions.assertTrue(filter.acceptReference("C:\\example\\file.com"));

        Assertions.assertFalse(
                filter.acceptReference("C:\\example\\dir.com\\file.pdf"));

        Assertions.assertTrue(filter.acceptReference("/tmp/file.com"));

        Assertions.assertFalse(filter.acceptReference("/tmp/dir.com/file.pdf"));

        Assertions.assertTrue(filter.acceptReference("file.com"));

        Assertions.assertFalse(filter.acceptReference("dir.com/file.pdf"));
    }

    private ExtensionReferenceFilter initFilter(String... extensions) {
        ExtensionReferenceFilter filter = new ExtensionReferenceFilter();
        filter.setExtensions(extensions);
        return filter;
    }

    @Test
    void testWriteRead() {
        ExtensionReferenceFilter f = new ExtensionReferenceFilter();
        f.setCaseSensitive(true);
        f.setExtensions("com","pdf");
        f.setOnMatch(OnMatch.EXCLUDE);
        XML.assertWriteRead(f, "filter");
    }
}
