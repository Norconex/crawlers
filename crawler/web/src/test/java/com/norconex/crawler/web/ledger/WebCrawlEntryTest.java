/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.web.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class WebCrawlEntryTest {

    @Test
    void testSetReferenceDerivesUrlRoot() {
        var entry = new WebCrawlEntry();
        entry.setReference("http://www.example.com/path/to/page.html");
        assertThat(entry.getUrlRoot()).isEqualTo("http://www.example.com");
    }

    @Test
    void testSetReferenceNullClearsUrlRoot() {
        var entry = new WebCrawlEntry("http://www.example.com/page.html");
        assertThat(entry.getUrlRoot()).isEqualTo("http://www.example.com");
        entry.setReference(null);
        assertThat(entry.getUrlRoot()).isNull();
    }

    @Test
    void testConstructorWithUrlAndDepth() {
        var entry = new WebCrawlEntry("https://example.com/doc", 3);
        assertThat(entry.getReference()).isEqualTo("https://example.com/doc");
        assertThat(entry.getDepth()).isEqualTo(3);
        assertThat(entry.getUrlRoot()).isEqualTo("https://example.com");
    }

    @Test
    void testCopyConstructorCopiesAllFields() {
        var src = new WebCrawlEntry("http://example.com/original", 2);
        src.setFromSitemap(true);
        src.setSitemapLastMod(ZonedDateTime.now());
        src.setSitemapChangeFreq("daily");
        src.setSitemapPriority(0.8f);
        src.setReferrerReference("http://example.com/referrer");
        src.setReferrerLinkMetadata("some metadata");
        src.setEtag("\"abc123\"");
        src.setHttpStatusCode(200);
        src.setHttpReasonPhrase("OK");
        src.setReferencedUrls(List.of("http://example.com/link1",
                "http://example.com/link2"));
        src.addRedirectURL("http://example.com/redirect-from");
        src.setRedirectTarget("http://example.com/redirect-to");

        var copy = new WebCrawlEntry(src);

        assertThat(copy.getReference()).isEqualTo(src.getReference());
        assertThat(copy.getDepth()).isEqualTo(src.getDepth());
        assertThat(copy.getUrlRoot()).isEqualTo(src.getUrlRoot());
        assertThat(copy.isFromSitemap()).isEqualTo(src.isFromSitemap());
        assertThat(copy.getSitemapLastMod()).isEqualTo(src.getSitemapLastMod());
        assertThat(copy.getSitemapChangeFreq())
                .isEqualTo(src.getSitemapChangeFreq());
        assertThat(copy.getSitemapPriority())
                .isEqualTo(src.getSitemapPriority());
        assertThat(copy.getReferrerReference())
                .isEqualTo(src.getReferrerReference());
        assertThat(copy.getReferrerLinkMetadata())
                .isEqualTo(src.getReferrerLinkMetadata());
        assertThat(copy.getEtag()).isEqualTo(src.getEtag());
        assertThat(copy.getHttpStatusCode()).isEqualTo(src.getHttpStatusCode());
        assertThat(copy.getHttpReasonPhrase())
                .isEqualTo(src.getHttpReasonPhrase());
        assertThat(copy.getReferencedUrls())
                .containsExactlyElementsOf(src.getReferencedUrls());
        assertThat(copy.getRedirectTrail())
                .containsExactlyElementsOf(src.getRedirectTrail());
        assertThat(copy.getRedirectTarget()).isEqualTo(src.getRedirectTarget());
    }

    @Test
    void testCopyConstructorProducesIndependentLists() {
        var src = new WebCrawlEntry("http://example.com/page", 0);
        src.setReferencedUrls(List.of("http://example.com/link1"));
        src.addRedirectURL("http://example.com/redirect-from");

        var copy = new WebCrawlEntry(src);

        // Mutating via src setters should not affect copy
        src.setReferencedUrls(List.of("http://example.com/other"));
        assertThat(copy.getReferencedUrls())
                .containsExactly("http://example.com/link1");
    }

    @Test
    void testAddRedirectUrl() {
        var entry = new WebCrawlEntry("http://example.com/final", 0);
        assertThat(entry.getRedirectTrail()).isEmpty();

        entry.addRedirectURL("http://example.com/first");
        entry.addRedirectURL("http://example.com/second");

        assertThat(entry.getRedirectTrail()).containsExactly(
                "http://example.com/first",
                "http://example.com/second");
    }

    @Test
    void testSetReferencedUrlsReplacesAll() {
        var entry = new WebCrawlEntry("http://example.com/page", 0);
        entry.setReferencedUrls(
                List.of("http://example.com/a", "http://example.com/b"));
        assertThat(entry.getReferencedUrls()).containsExactly(
                "http://example.com/a", "http://example.com/b");

        entry.setReferencedUrls(List.of("http://example.com/c"));
        assertThat(entry.getReferencedUrls())
                .containsExactly("http://example.com/c");
    }

    @Test
    void testSetRedirectTrailReplacesAll() {
        var entry = new WebCrawlEntry("http://example.com/last", 0);
        entry.setRedirectTrail(
                List.of("http://example.com/r1", "http://example.com/r2"));
        assertThat(entry.getRedirectTrail()).containsExactly(
                "http://example.com/r1", "http://example.com/r2");

        entry.setRedirectTrail(List.of("http://example.com/r3"));
        assertThat(entry.getRedirectTrail())
                .containsExactly("http://example.com/r3");
    }

    @Test
    void testSitemapFields() {
        var entry = new WebCrawlEntry();
        var now = ZonedDateTime.now();
        entry.setFromSitemap(true);
        entry.setSitemapLastMod(now);
        entry.setSitemapChangeFreq("weekly");
        entry.setSitemapPriority(0.5f);

        assertThat(entry.isFromSitemap()).isTrue();
        assertThat(entry.getSitemapLastMod()).isEqualTo(now);
        assertThat(entry.getSitemapChangeFreq()).isEqualTo("weekly");
        assertThat(entry.getSitemapPriority()).isEqualTo(0.5f);
    }

    @Test
    void testHttpStatusAndEtag() {
        var entry = new WebCrawlEntry("http://example.com/", 0);
        entry.setHttpStatusCode(304);
        entry.setHttpReasonPhrase("Not Modified");
        entry.setEtag("\"strongEtag\"");

        assertThat(entry.getHttpStatusCode()).isEqualTo(304);
        assertThat(entry.getHttpReasonPhrase()).isEqualTo("Not Modified");
        assertThat(entry.getEtag()).isEqualTo("\"strongEtag\"");
    }
}
