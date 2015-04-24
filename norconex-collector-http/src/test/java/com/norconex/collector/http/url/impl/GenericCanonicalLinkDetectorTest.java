/* Copyright 2015 Norconex Inc.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.file.ContentType;

/**
 * 
 * @author Pascal Essiembre
 * @since 2.2.0
 */
public class GenericCanonicalLinkDetectorTest {

    @Test
    public void testDetectFromMetadata() throws IOException {
        String reference = "http://www.example.com/file.pdf";
        GenericCanonicalLinkDetector d = new GenericCanonicalLinkDetector();
        HttpMetadata metadata = new HttpMetadata(reference);
        String canonURL = "http://www.example.com/canonical.pdf";
        String url = null;
        
        // Test absolute
        metadata.setString("Link", "<" + canonURL + "> rel=\"canonical\"");
        url = d.detectFromMetadata(reference, metadata);
        Assert.assertEquals("Invalid absolute canonical URL", canonURL, url);

        // Test relative 
        String relCanonical = "/canonical.pdf";
        metadata.setString("Link", "<" + relCanonical + "> rel=\"canonical\"");
        url = d.detectFromMetadata(reference, metadata);
        Assert.assertEquals("Invalid relative canonical URL", canonURL, url);
    }

    @Test
    public void testDetectFromContent() throws IOException {
        String reference = "http://www.example.com/file.pdf";
        GenericCanonicalLinkDetector d = new GenericCanonicalLinkDetector();
        String canonURL = "http://www.example.com/canonical.pdf";
        String url = null;
        
        // Valid link tag
        String contentValid = "<html><head><title>Test</title>\n"
                + "<link rel=\"canonical\"\n href=\"\n" + canonURL +  "\" />\n"
                + "</head><body>Nothing of interest in body</body></html>";
        url = d.detectFromContent(reference,  new ByteArrayInputStream(
                contentValid.getBytes()), ContentType.HTML);
        Assert.assertEquals("Invalid <link> form <head>", canonURL, url);
        
        // Invalid location for link tag
        String contentInvalid = "<html><head><title>Test</title>\n"
                + "</head><body>\n"
                + "<link rel=\"canonical\"\n href=\"\n" + canonURL +  "\" />\n"
                + "</body></html>";
        url = d.detectFromContent(reference,  new ByteArrayInputStream(
                contentInvalid.getBytes()), ContentType.HTML);
        Assert.assertNull("Canonical link should be null.",  url);
    }
    
    @Test
    public void testWriteRead() throws IOException {
        GenericCanonicalLinkDetector d = new GenericCanonicalLinkDetector();
        d.setContentTypes(ContentType.HTML, ContentType.TEXT);
        System.out.println("Writing/Reading this: " + d);
        ConfigurationUtil.assertWriteRead(d);
    }

}
