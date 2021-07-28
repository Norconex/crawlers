/* Copyright 2019-2021 Norconex Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.xml.XML;

public class GenericSitemapResolverTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            GenericSitemapResolverTest.class);

    @Test
    public void testSitemapResolverParsing()
            throws IOException, XMLStreamException {

        List<HttpDocInfo> extractedLinks = new ArrayList<>();
        GenericSitemapResolver r = new GenericSitemapResolver();
        try (InputStream is = ResourceLoader.getXmlStream(getClass())) {
            r.parseSitemap(is, null, d -> {
                extractedLinks.add(d);
            }, new HashSet<>(), "https://example.com/sitemap.xml");
        }

        // All links there?
        Assertions.assertEquals(
                Arrays.asList(
                        "https://example.com/linkA",
                        "https://example.com/linkB",
                        "https://example.com/linkC"),
                extractedLinks.stream()
                        .map(HttpDocInfo::getReference)
                        .collect(Collectors.toList()));

        // test second one:
        HttpDocInfo doc = extractedLinks.get(1);
        Assertions.assertEquals(
                "https://example.com/linkB", doc.getReference());
        Assertions.assertEquals("2021-04-01",
                doc.getSitemapLastMod().toLocalDate().toString());
        Assertions.assertEquals("daily", doc.getSitemapChangeFreq());
        Assertions.assertEquals(1f, doc.getSitemapPriority());
    }

    @Test
    public void testWriteRead() {
        GenericSitemapResolver r = new GenericSitemapResolver();
        r.setLenient(true);
        r.setTempDir(Paths.get("C:\\temp\\sitemap"));
        r.setSitemapPaths("/sitemap.xml", "/subdir/sitemap.xml");
        LOG.debug("Writing/Reading this: {}", r);
        XML.assertWriteRead(r, "sitemapResolver");

        // try with empty paths
        r.setSitemapPaths(new String[] {});
        LOG.debug("Writing/Reading this: {}", r);
        XML.assertWriteRead(r, "sitemapResolver");
    }

}
