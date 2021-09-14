/* Copyright 2010-2021 Norconex Inc.
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
package com.norconex.collector.http.sitemap.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.joda.time.LocalDate;
import org.junit.Assert;
import org.junit.Test;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.sitemap.SitemapURLAdder;
import com.norconex.collector.http.sitemap.impl.StandardSitemapResolver.ParseContext;
import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.config.XMLConfigurationUtil;

public class StandardSitemapResolverTest {

    @Test
    public void testSitemapResolverParsing()
            throws IOException, XMLStreamException {

        StandardSitemapResolverFactory f = new StandardSitemapResolverFactory();
        f.setEscalateErrors(true);
        StandardSitemapResolver r =
                (StandardSitemapResolver) f.createSitemapResolver(
                        new HttpCrawlerConfig(), false);

        final List<HttpCrawlData> extractedLinks = new ArrayList<>();
        ParseContext parseContext = new ParseContext(
                new SitemapURLAdder() {
                    @Override
                    public void add(HttpCrawlData crawlData) {
                        extractedLinks.add(crawlData);
                    }
                },
                null);

        try (InputStream is = ResourceLoader.getXmlStream(getClass())) {
            r.parseXml("https://example.com/", is, parseContext);
        }

        // All links there?
        List<String> urls = new ArrayList<>();
        for (HttpCrawlData httpCrawlData : extractedLinks) {
            urls.add(httpCrawlData.getReference());
        }

        Assert.assertEquals(
                Arrays.asList(
                        "https://example.com/linkA",
                        "https://example.com/linkB",
                        "https://example.com/linkC",
                        "https://example.com/linkD"),
                urls);

        // test second one:
        HttpCrawlData doc = extractedLinks.get(1);
        Assert.assertEquals(
                "https://example.com/linkB", doc.getReference());
        Assert.assertEquals("2021-04-01",
                new LocalDate(doc.getSitemapLastMod()).toString());
        Assert.assertEquals("daily", doc.getSitemapChangeFreq());
        Assert.assertEquals(Float.valueOf(1f), doc.getSitemapPriority());
    }

    @Test
    public void testWriteRead() throws IOException {
        StandardSitemapResolverFactory r = new StandardSitemapResolverFactory();
        r.setLenient(true);
        r.setTempDir(new File("C:\\temp\\sitemap"));
        r.setSitemapPaths("/sitemap.xml", "/subdir/sitemap.xml");
        System.out.println("Writing/Reading this: " + r);
        XMLConfigurationUtil.assertWriteRead(r);

        // try with empty paths
        r.setSitemapPaths(new String[] {});
        System.out.println("Writing/Reading this: " + r);
        XMLConfigurationUtil.assertWriteRead(r);

    }


    @Test
    public void testTolerantParser() throws IOException, XMLStreamException {

        try (FileInputStream fis = new FileInputStream(
                new File("src/test/resources/sitemap.xml"))) {
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            inputFactory.setProperty(XMLInputFactory.IS_COALESCING,true);
            XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(
                    new StripInvalidCharInputStream(fis));
            xmlReader.getEventType();
            while(true){
                if (!xmlReader.hasNext()) {
                    break;
                }
                xmlReader.next();
            }
        }
    }
}
