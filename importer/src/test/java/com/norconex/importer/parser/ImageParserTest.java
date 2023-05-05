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
package com.norconex.importer.parser;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ImageParserTest extends AbstractParserTest {

    @Test
    void testWEBP() throws Exception {
        testParsing("image/webp", "webp");
    }
    @Test
    void testBMP() throws Exception {
        testParsing("image/bmp", "bmp");
    }
    @Test
    void testGIF() throws Exception {
        testParsing("image/gif", "gif");
    }
    @Test
    void testJPG() throws Exception {
        testParsing("image/jpeg", "jpg");
    }
    @Test
    void testJPG_XMP() throws Exception {
        // JPEG with XMP metadata.  Can be dealt with, with a tool such
        // as http://www.exiv2.org
        // Currently parsed by Tika using Jempbox
        var responses = testParsing("/parser/image/importer-xmp.jpg",
                "image/jpeg", ".*", "jpg", "Image");
        var meta = responses[0].getDocument().getMetadata();
        Assertions.assertEquals(
                "XMP Parsing", meta.getString("dc:subject"),
                "Could not find XMP metadata dc:subject with "
                        + "expected value \"XML Parsing\".");
    }
    @Test
    void testPNG() throws Exception {
        testParsing("image/png", "png");
    }
    @Test
    void testPSD() throws Exception {
        testParsing("/parser/image/importer.psd",
                "image/vnd.adobe.photoshop", ".*", "psd", "Image");
    }
    @Test
    void testTIF() throws Exception {
        testParsing("/parser/image/importer.tif",
                "image/tiff", ".*", "tiff", "Image");
    }

    @Test
    void testJBIG2() throws Exception {
        var responses = testParsing("/parser/image/importer.jb2",
                "image/x-jbig2", ".*", "jb2", "Image");
        var meta = responses[0].getDocument().getMetadata();

        Assertions.assertEquals(
                "125", meta.getString("width"),
                "Image 'width' not extracted or invalid");
        Assertions.assertEquals(
                "16", meta.getString("height"),
                "Image 'height' not extracted or invalid");
    }

    private void testParsing(String contentType, String extension)
            throws Exception {
        testParsing("/parser/image/importer." + extension,
                contentType, ".*", extension, "Image");
    }
}
