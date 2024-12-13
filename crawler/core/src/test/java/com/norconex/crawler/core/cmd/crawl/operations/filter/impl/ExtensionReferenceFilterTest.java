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
package com.norconex.crawler.core.cmd.crawl.operations.filter.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.cmd.crawl.operations.filter.OnMatch;
import com.norconex.crawler.core.stubs.CrawlDocStubs;

class ExtensionReferenceFilterTest {

    @Test
    void testOnlyDetectExtensionsInLastPathSegment() {
        var filter = initFilter(Set.of("com", "xml"));

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

    @Test
    void testEmpty() {
        var f = new ExtensionReferenceFilter();
        assertThat(f.acceptReference("ref")).isTrue();
        assertThat(f.getOnMatch()).isSameAs(OnMatch.INCLUDE);
        assertThat(f.getConfiguration().getExtensions()).isEmpty();

        // here we exclude if it matches. since it does not match, it returns
        // true (record is accepted)
        f = new ExtensionReferenceFilter();
        f.getConfiguration()
                .setExtensions(Set.of("blah"))
                .setOnMatch(OnMatch.EXCLUDE);
        assertThat(f.acceptReference("")).isTrue();
    }

    @Test
    void testDocumentAndMetadata() {
        var f = new ExtensionReferenceFilter();
        f.getConfiguration()
                .setExtensions(Set.of("pdf"));
        assertThat(
                f.acceptDocument(
                        CrawlDocStubs.crawlDoc(
                                "http://example.com/test.pdf"))).isTrue();
        assertThat(
                f.acceptMetadata(
                        "http://example.com/test.pdf", new Properties()))
                                .isTrue();
    }

    private ExtensionReferenceFilter initFilter(Set<String> extensions) {
        var filter = new ExtensionReferenceFilter();
        filter.getConfiguration().setExtensions(extensions);
        return filter;
    }

    @Test
    void testWriteRead() {
        var f = new ExtensionReferenceFilter();
        f.getConfiguration()
                .setIgnoreCase(true)
                .setExtensions(Set.of("com", "pdf"))
                .setOnMatch(OnMatch.EXCLUDE);
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(f));
    }
}
