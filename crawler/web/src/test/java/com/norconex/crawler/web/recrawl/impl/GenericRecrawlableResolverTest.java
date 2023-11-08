/* Copyright 2016-2023 Norconex Inc.
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
package com.norconex.crawler.web.recrawl.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.recrawl.impl.GenericRecrawlableResolver.MinFrequency;
import com.norconex.crawler.web.recrawl.impl.GenericRecrawlableResolver.SitemapSupport;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class GenericRecrawlableResolverTest {

    @Test
    void testWriteRead() {
        var r = new GenericRecrawlableResolver();
        r.setSitemapSupport(SitemapSupport.LAST);

        var f1 = new MinFrequency("reference", "monthly",
                TextMatcher.regex(".*\\.pdf").ignoreCase());
        var f2 = new MinFrequency("contentType", "1234",
                TextMatcher.regex(".*"));

        r.setMinFrequencies(List.of(f1, f2));

        LOG.debug("Writing/Reading this: {}", r);
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(r));
    }

    // Test for: https://github.com/Norconex/collector-http/issues/597
    @Test
    void testCustomFrequency() {
        var r = new GenericRecrawlableResolver();
        r.setSitemapSupport(SitemapSupport.NEVER);

        var prevCrawlDate = ZonedDateTime.now().minusDays(10);

        var prevCrawl = new WebDocRecord();
        prevCrawl.setContentType(ContentType.HTML);
        prevCrawl.setReference("http://example.com");
        prevCrawl.setCrawlDate(prevCrawlDate);

        var f = new MinFrequency(
                "reference", "120 days", TextMatcher.regex(".*"));

        r.setMinFrequencies(List.of(f));
        Assertions.assertFalse(r.isRecrawlable(prevCrawl));

        // Delay has passed
        f = new MinFrequency("reference", "5 days", TextMatcher.regex(".*"));
        r.setMinFrequencies(List.of(f));
        Assertions.assertTrue(r.isRecrawlable(prevCrawl));
    }

    @Test
    void testCustomFrequencyFromXML() {
        var xml = """
            <recrawlableResolver
                class="com.norconex.crawler.web.recrawl.impl.\
            GenericRecrawlableResolver" sitemapSupport="never">
              <minFrequency applyTo="reference" value="120 days">
                <matcher method="regex">.*</matcher>
              </minFrequency>
            </recrawlableResolver>""";
        var r = new GenericRecrawlableResolver();
        new XML(xml).populate(r);
        var prevCrawlDate = ZonedDateTime.now().minusDays(10);
        var prevCrawl = new WebDocRecord();
        prevCrawl.setContentType(ContentType.HTML);
        prevCrawl.setReference("http://example.com");
        prevCrawl.setCrawlDate(prevCrawlDate);

        Assertions.assertFalse(r.isRecrawlable(prevCrawl));
    }


    @ParameterizedTest
    @CsvSource(
        nullValues = "NULL",
        textBlock = """
            first, NULL,    NULL, reference,   hourly,  true,
            first, hourly,  NULL, reference,   hourly,  true,
            first, hourly,  NULL, reference,   always,  true,
            first, hourly,  NULL, reference,   never,   true,
            first, hourly,  NULL, reference,   yearly,  true,
            last,  NULL,    NULL, reference,   hourly,  true,
            last,  hourly,  NULL, reference,   hourly,  true,
            last,  hourly,  NULL, reference,   always,  true,
            last,  hourly,  NULL, reference,   never,   false,
            last,  hourly,  NULL, reference,   yearly,  false,
            last,  daily,   NULL, reference,   NULL,    true,
            last,  monthly, NULL, reference,   NULL,    true,
            first, daily,   NULL, reference,   NULL,    true,
            first, weekly,  NULL, reference,   NULL,    false,
            first, monthly, NULL, reference,   NULL,    false,
            last,  hourly,  NULL, contentType, hourly,  true,
            last,  hourly,  NULL, contentType, always,  true,
            last,  hourly,  NULL, contentType, never,   false,
            first, monthly, 2,    contentType, monthly, true,
            first, monthly, 2,    contentType, monthly, true,
            """
    )
    void testIsRecrawlable(
            String sitemapSupport,
            String sitemapChangeFreq,
            Integer sitemapLastModDays,
            String minFreqApplyTo,
            String minFreqValue,
            boolean expected) {
        WebDocRecord prevRec;
        var url = "http://test.com/yes.html";
        var resolver = new GenericRecrawlableResolver();
        prevRec = new WebDocRecord(url);
        prevRec.setContentType(ContentType.HTML);
        prevRec.setCrawlDate(ZonedDateTime.now().minusDays(3));
        prevRec.setSitemapChangeFreq(sitemapChangeFreq);
        if (sitemapLastModDays != null) {
            prevRec.setSitemapLastMod(
                    ZonedDateTime.now().minusDays(sitemapLastModDays));
        }

        resolver.setSitemapSupport(
                SitemapSupport.getSitemapSupport(sitemapSupport));

        var matcher = "reference".equals(minFreqApplyTo)
                ? TextMatcher.basic(url) : TextMatcher.basic("text/html");
        resolver.setMinFrequencies(List.of(new MinFrequency(
                minFreqApplyTo, minFreqValue, matcher)));

        assertThat(resolver.isRecrawlable(prevRec)).isEqualTo(expected);
    }

    @Test
    void testNullAndEmpties() {
        assertThat(SitemapSupport.getSitemapSupport(null)).isNull();
        assertThat(SitemapSupport.getSitemapSupport("iDontExist")).isNull();

        // no last crawl date
        assertThat(new GenericRecrawlableResolver().isRecrawlable(
                new WebDocRecord("http://blah.com"))).isTrue();
    }
}
