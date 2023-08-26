/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.web.canon.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.DocMetadata;

class GenericCanonicalLinkDetectorTest {

    @Test
    void testDetectFromMetadata() {
        var reference = "http://www.example.com/file.pdf";
        var d = new GenericCanonicalLinkDetector();
        var metadata = new Properties();
        metadata.set(DocMetadata.REFERENCE, reference);
        var canonURL = "http://www.example.com/canonical.pdf";
        String url;

        // Test absolute
        metadata.set("Link", "<" + canonURL + "> rel=\"canonical\"");
        url = d.detectFromMetadata(reference, metadata);
        Assertions.assertEquals(
                canonURL, url, "Invalid absolute canonical URL");

        // Test relative
        var relCanonical = "/canonical.pdf";
        metadata.set("Link", "<" + relCanonical + "> rel=\"canonical\"");
        url = d.detectFromMetadata(reference, metadata);
        Assertions.assertEquals(
                canonURL, url, "Invalid relative canonical URL");
    }

    @Test
    void testDetectFromContent() throws IOException {
        var reference = "http://www.example.com/file.pdf";
        var d = new GenericCanonicalLinkDetector();
        var canonURL = "http://www.example.com/canonical.pdf";
        String url;

        // Valid link tag
        var contentValid = "<html><head><title>Test</title>\n"
                + "<link rel=\"canonical\"\n href=\"\n" + canonURL +  "\" />\n"
                + "</head><body>Nothing of interest in body</body></html>";
        url = d.detectFromContent(reference,  new ByteArrayInputStream(
                contentValid.getBytes()), ContentType.HTML);
        Assertions.assertEquals(canonURL, url, "Invalid <link> form <head>");

        // Invalid location for link tag
        var contentInvalid = "<html><head><title>Test</title>\n"
                + "</head><body>\n"
                + "<link rel=\"canonical\"\n href=\"\n" + canonURL +  "\" />\n"
                + "</body></html>";
        url = d.detectFromContent(reference,  new ByteArrayInputStream(
                contentInvalid.getBytes()), ContentType.HTML);
        Assertions.assertNull(url, "Canonical link should be null.");
    }

    // Test for: https://github.com/Norconex/collector-http/issues/646
    @Test
    void testMultipleLinkValueFromMetadata() {
        var reference = "http://www.example.com/file.pdf";
        var d = new GenericCanonicalLinkDetector();
        var metadata = new Properties();
        metadata.set(DocMetadata.REFERENCE, reference);
        var canonURL = "http://www.example.com/cano,ni;cal.pdf";
        String url;

        // Test "Link:" with multiple values
        metadata.set("Link",
                "<http://www.example.com/images/logo.png>; rel=\"image_src\","
              + "<" + canonURL + ">; rel=\"canonical\","
              + "<http://www.example.com/short/1234>; rel=\"shortlink\","
              + "<" + canonURL + ">; rel=\"hreflang_en\"");
        url = d.detectFromMetadata(reference, metadata);
        Assertions.assertEquals(
                canonURL, url, "Canonical URL not detected properly.");
    }



    @Test
    void testEscapedCanonicalUrl() throws IOException {
        var reference = "http://www.test.te.com/web";
        var d = new GenericCanonicalLinkDetector();
        var escapedCanonicalUrl =
                "https&#x3a;&#x2f;&#x2f;test&#x2e;kaffe&#x2e;se&#x2f;web";
        var unescapedCanonicalUrl = "https://test.kaffe.se/web";
        String url;

        // Valid link tag
        var contentValid = "<html><head><title>Test</title>\n"
                + "<link rel=\"canonical\"\n href=\"\n" + escapedCanonicalUrl
                + "\" />\n"
                + "</head><body>Nothing of interest in body</body></html>";
        url = d.detectFromContent(reference,  new ByteArrayInputStream(
                contentValid.getBytes()), ContentType.HTML);
        Assertions.assertEquals(unescapedCanonicalUrl, url);
    }

    // Testing that single quotes within double quotes do no break extraction
    // https://github.com/Norconex/collector-http/issues/111
    @Test
    void testMixedQuoteCanonicalUrl() throws IOException {
        var reference = "http://www.test.te.com/web";
        var d = new GenericCanonicalLinkDetector();
        var sourceUrl = "http://www.example.com/blah'blah.html";
        var targetUrl = "http://www.example.com/blah'blah.html";
        String extractedUrl;

        // Valid link tag
        var contentValid = "<html><head><title>Test</title>\n"
                + "<link rel=\"canonical\"\n href=\"\n" + sourceUrl
                + "\" />\n"
                + "</head><body>Nothing of interest in body</body></html>";
        extractedUrl = d.detectFromContent(reference,  new ByteArrayInputStream(
                contentValid.getBytes()), ContentType.HTML);
        Assertions.assertEquals(targetUrl, extractedUrl);
    }


//    @Test
//    void testWriteRead() {
//        var d = new GenericCanonicalLinkDetector();
//        d.setContentTypes(List.of(ContentType.HTML, ContentType.TEXT));
//        assertThatNoException().isThrownBy(() ->
//                XML.assertWriteRead(d, "canonicalLinkDetector"));
//    }
}
