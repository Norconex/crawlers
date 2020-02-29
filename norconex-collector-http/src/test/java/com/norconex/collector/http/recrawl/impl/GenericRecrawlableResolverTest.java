/* Copyright 2016-2020 Norconex Inc.
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
package com.norconex.collector.http.recrawl.impl;

import java.io.IOException;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.recrawl.PreviousCrawlData;
import com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver.MinFrequency;
import com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver.SitemapSupport;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.xml.XML;
public class GenericRecrawlableResolverTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            GenericRecrawlableResolverTest.class);

    @Test
    public void testWriteRead() throws IOException {
        GenericRecrawlableResolver r = new GenericRecrawlableResolver();
        r.setSitemapSupport(SitemapSupport.LAST);

        MinFrequency f1 = new MinFrequency("reference", "monthly", ".*\\.pdf");
        MinFrequency f2 = new MinFrequency("contentType", "1234", ".*");
        f2.setCaseSensitive(true);

        r.setMinFrequencies(f1, f2);

        LOG.debug("Writing/Reading this: {}", r);
        XML.assertWriteRead(r, "recrawlableResolver");
    }

    // Test for: https://github.com/Norconex/collector-http/issues/597
    @Test
    public void testCustomFrequency() throws IOException {
        GenericRecrawlableResolver r = new GenericRecrawlableResolver();
        r.setSitemapSupport(SitemapSupport.NEVER);

        ZonedDateTime prevCrawlDate = ZonedDateTime.now().minusDays(10);

        PreviousCrawlData prevCrawl = new PreviousCrawlData();
        prevCrawl.setContentType(ContentType.HTML);
        prevCrawl.setReference("http://example.com");
        prevCrawl.setCrawlDate(prevCrawlDate);

        MinFrequency f = null;

        // Delay has not yet passed
        f = new MinFrequency("reference", "120 days", ".*");
        r.setMinFrequencies(f);
        Assertions.assertFalse(r.isRecrawlable(prevCrawl));

        // Delay has passed
        f = new MinFrequency("reference", "5 days", ".*");
        r.setMinFrequencies(f);
        Assertions.assertTrue(r.isRecrawlable(prevCrawl));
    }

    @Test
    public void testCustomFrequencyFromXML() throws IOException {
        String xml = "<recrawlableResolver class=\""
                + "com.norconex.collector.http.recrawl.impl."
                + "GenericRecrawlableResolver\" sitemapSupport=\"never\">"
                + "<minFrequency applyTo=\"reference\" value=\"120 days\">"
                + ".*</minFrequency>"
                + "</recrawlableResolver>";
        GenericRecrawlableResolver r = new GenericRecrawlableResolver();
        r.loadFromXML(new XML(xml));

        ZonedDateTime prevCrawlDate = ZonedDateTime.now().minusDays(10);
        PreviousCrawlData prevCrawl = new PreviousCrawlData();
        prevCrawl.setContentType(ContentType.HTML);
        prevCrawl.setReference("http://example.com");
        prevCrawl.setCrawlDate(prevCrawlDate);

        Assertions.assertFalse(r.isRecrawlable(prevCrawl));
    }
}
