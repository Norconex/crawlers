/* Copyright 2010-2016 Norconex Inc.
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
package com.norconex.collector.http.url.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Assert;
import org.junit.Test;

import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.collector.http.url.Link;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.file.ContentType;


/**
 * Tests multiple {@link ILinkExtractor} implementations.
 * @author Pascal Essiembre
 */
public class LinkExtractorTest {

    //--- Common tests ---------------------------------------------------------
    @Test
    public void testGenericLinkExtractor() throws IOException {
        GenericLinkExtractor ex = new GenericLinkExtractor();
        ex.addLinkTag("link", null);
        testLinkExtraction(ex);
    }
    @Test
    public void testTikaLinkExtractor() throws IOException {
        testLinkExtraction(new TikaLinkExtractor());
    }
    private void testLinkExtraction(ILinkExtractor extractor) 
            throws IOException {
        String baseURL = "http://www.example.com/";
        String baseDir = baseURL + "test/";
        String docURL = baseDir + "LinkExtractorTest.html";

        // All these must be found
        String[] expectedURLs = {
                baseURL + "meta-redirect.html",
                baseURL + "startWithDoubleslash.html",
                docURL + "?startWith=questionmark",
                docURL + "#startWithHashMark",
                baseURL + "startWithSlash.html",
                baseDir + "relativeToLastSegment.html",
                "http://www.sample.com/blah.html",
                baseURL + "onTwoLines.html",
                baseURL + "imageSlash.gif",
                baseURL + "imageNoSlash.gif",
                baseDir + "titleTarget.html",
                baseURL + "htmlEntities",
                baseURL + "?p1=v1&p2=v2&p3=v3",
        };
        // All these must NOT be found
        String[] unexpectedURLs = {
                baseURL + "badhref.html",
                baseURL + "nofollow.html",
                baseURL + "/dont/process/scripts/'+variable+'",
                baseURL + "/dont/process/a/'+inscript+'",
                baseDir, // empty href
        };

        // Only GenericLinkExtractor:
        if (extractor instanceof GenericLinkExtractor) {
            String[] additionalURLs = {
                    baseURL + "addedTagNoAttribUrlInBody.html",
                    baseURL + "addedTagAttribUrlInBody.html",
            };
            expectedURLs = ArrayUtils.addAll(expectedURLs, additionalURLs);

            String[] fewerURLs = {
                    "tel:123",
                    "mailto:blah@blah.com"
            };
            unexpectedURLs = ArrayUtils.addAll(unexpectedURLs, fewerURLs);
        }
        // Only TikaLinkExtractor:
        if (extractor instanceof TikaLinkExtractor) {
            String[] additionalURLs = {
                    "tel:123",
                    "mailto:blah@blah.com"
            };
            expectedURLs = ArrayUtils.addAll(expectedURLs, additionalURLs);
        }        
        
        InputStream is = getClass().getResourceAsStream(
                "LinkExtractorTest.html");

        Set<Link> links = extractor.extractLinks(
                is, docURL, ContentType.HTML);
        IOUtils.closeQuietly(is);

        for (String expectedURL : expectedURLs) {
            assertTrue("Could not find expected URL: " + expectedURL, 
                    contains(links, expectedURL));
        }
        for (String unexpectedURL : unexpectedURLs) {
            assertFalse("Found unexpected URL: " + unexpectedURL, 
                    contains(links, unexpectedURL));
        }

        Assert.assertEquals("Invalid number of links extracted.", 
                expectedURLs.length, links.size());
    }    

    //--- BASE HREF Tests ------------------------------------------------------
    @Test
    public void testGenericBaseHrefLinkExtractor() throws IOException {
        GenericLinkExtractor ex = new GenericLinkExtractor();
        testBaseHrefLinkExtraction(ex);
    }
    @Test
    public void testTikaBaseHrefLinkExtractor() throws IOException {
        testBaseHrefLinkExtraction(new TikaLinkExtractor());
    }
    private void testBaseHrefLinkExtraction(ILinkExtractor extractor) 
            throws IOException {
        String docURL = "http://www.example.com/test/absolute/"
                + "LinkBaseHrefTest.html";
        String host = "http://www.sample.com";
        String baseURL = host + "/blah/";

        // All these must be found
        String[] expectedURLs = {
                baseURL + "a/b/c.html",
                host + "/d/e/f.html",
                "http://www.sample.com/g/h/i.html",
                "http://www.anotherhost.com/k/l/m.html",
        };
        
        InputStream is = 
                getClass().getResourceAsStream("LinkBaseHrefTest.html");
        Set<Link> links = extractor.extractLinks(
                is, docURL, ContentType.HTML);
        IOUtils.closeQuietly(is);
        for (String expectedURL : expectedURLs) {
            assertTrue("Could not find expected URL: " + expectedURL, 
                    contains(links, expectedURL));
        }
        Assert.assertEquals("Invalid number of links extracted.", 
                expectedURLs.length, links.size());
    }
    
    //--- Referrer Data Tests --------------------------------------------------    
    @Test
    public void testGenericLinkKeepReferrer() throws IOException {
        GenericLinkExtractor extractor = new GenericLinkExtractor();
        extractor.setContentTypes(ContentType.HTML);
        testLinkKeepReferrer(extractor);
    }
    @Test
    public void testTikaLinkKeepReferrer() throws IOException {
        TikaLinkExtractor extractor = new TikaLinkExtractor();
        extractor.setContentTypes(ContentType.HTML);
        testLinkKeepReferrer(extractor);
    }
    private void testLinkKeepReferrer(ILinkExtractor extractor)
            throws IOException {
        // All these must be found
        Link[] expectedLinks = {
            keepReferrerLink("1-notitle-notext.html", null, null),
            keepReferrerLink("2-notitle-yestext.html", "2 Yes Text", null),
            keepReferrerLink(
                    "3-yestitle-yestext.html", "3 Yes Text", "3 Yes Title"),
            keepReferrerLink("4-yestitle-notext.html", null, "4 Yes Title"),
            // Link 5 should not be there (no href).
            keepReferrerLink("6-yestitle-yestexthtml.html", 
                    "[6]Yes Text", "6 Yes Title"),
        };        
        
        InputStream is = 
                getClass().getResourceAsStream("LinkKeepReferrerTest.html");
        Set<Link> links = extractor.extractLinks(
                is, "http://www.site.com/parent.html", ContentType.HTML);
        IOUtils.closeQuietly(is);

        Assert.assertEquals(expectedLinks.length, links.size());
        for (Link expectedLink : expectedLinks) {
            assertTrue("Could not find expected link: " + expectedLink, 
                    contains(links, expectedLink));
        }
    }
    private Link keepReferrerLink(
            String relURL, String text, String title) {
        Link link = new Link("http://www.site.com/" + relURL);
        link.setReferrer("http://www.site.com/parent.html");
        link.setTag("a.href");
        link.setText(text);
        link.setTitle(title);
        return link;
    }    

    
    //--- Other Tests ----------------------------------------------------------    
    @Test
    public void testGenericWriteRead() throws IOException {
        GenericLinkExtractor extractor = new GenericLinkExtractor();
        extractor.setContentTypes(ContentType.HTML, ContentType.XML);
        extractor.setIgnoreNofollow(true);
        extractor.addLinkTag("food", "chocolate");
        extractor.addLinkTag("friend", "Thor");
        System.out.println("Writing/Reading this: " + extractor);
        ConfigurationUtil.assertWriteRead(extractor);
    }

    @Test
    public void testGenericEquivRefreshIssue210()
            throws IOException {
        String html = "<html><head><meta http-equiv=\"refresh\" "
                + "content=\"0; URL=en/91/index.html\">"
                + "</head><body></body></html>";
        String docURL = "http://db-artmag.com/index_en.html";
        ILinkExtractor extractor = new GenericLinkExtractor();
        Set<Link> links = extractor.extractLinks(
                new ByteArrayInputStream(html.getBytes()),
                docURL, ContentType.HTML);
        
        Assert.assertEquals(
                "Invalid number of links extracted.", 1, links.size());
        Assert.assertEquals(
                "http://db-artmag.com/en/91/index.html",
                links.iterator().next().getUrl());
    }    
    
    @Test
    public void testTikaWriteRead() throws IOException {
        TikaLinkExtractor extractor = new TikaLinkExtractor();
        extractor.setContentTypes(ContentType.HTML, ContentType.XML);
        extractor.setIgnoreNofollow(true);
        System.out.println("Writing/Reading this: " + extractor);
        ConfigurationUtil.assertWriteRead(extractor);
    }

    
    @Test
    public void testIssue188() throws IOException {
        String ref = "http://www.site.com/en/articles/articles.html"
                + "?param1=value1&param2=value2";
        String url = "http://www.site.com/en/articles/detail/article-x.html";
        String html = "<html><body>"
                + "<a href=\"/en/articles/detail/article-x.html\">test link</a>"
                + "</body></html>";
        ByteArrayInputStream input = new ByteArrayInputStream(html.getBytes());
        GenericLinkExtractor extractor = new GenericLinkExtractor();
        Set<Link> links = extractor.extractLinks(input, ref, ContentType.HTML);
        input.close();
        Assert.assertTrue("URL not extracted: " + url, contains(links, url));
    }
    
    @Test
    public void testIssue236() throws IOException {
        String url = "javascript:__doPostBack('MoreInfoList1$Pager','2')";
        String html = "<html><body>"
                + "<a href=\"" + url + "\">JavaScript link</a>"
                + "</body></html>";
        ByteArrayInputStream input = new ByteArrayInputStream(html.getBytes());
        GenericLinkExtractor extractor = new GenericLinkExtractor();
        extractor.setSchemes("javascript");
        Set<Link> links = extractor.extractLinks(
                input, "N/A", ContentType.HTML);
        input.close();
        Assert.assertTrue("URL not extracted: " + url, contains(links, url));
    }
    
    private boolean contains(Set<Link> links, String url) {
        for (Link link : links) {
            if (url.equals(link.getUrl())) {
                return true;
            }
        }
        return false;
    }
    private boolean contains(Set<Link> links, Link link) {
        for (Link l : links) {
            if (link.equals(l)) {
                return true;
            }
        }
        return false;
    }
}
