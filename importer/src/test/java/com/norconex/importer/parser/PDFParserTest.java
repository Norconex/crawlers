/* Copyright 2015-2023 Norconex Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

public class PDFParserTest extends AbstractParserTest {

    private static final String PDF_FAMILY = "Portable Document Format (PDF)";

    @Test
    public void test_PDF_plain() throws IOException {
        testParsing("/parser/pdf/plain.pdf", "application/pdf",
                DEFAULT_CONTENT_REGEX, "pdf", PDF_FAMILY);
    }

    @Test
    public void test_PDF_jpeg() throws IOException {
        testParsing("/parser/pdf/jpeg.pdf", "application/pdf",
                ".*PDF with a JPEG image.*", "pdf", PDF_FAMILY, true);
    }

    @Test
    public void test_PDF_jbig2()
            throws IOException, SAXException, TikaException {
        RecursiveParserWrapperHandler h = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory
                        .HANDLER_TYPE.IGNORE, -1));

        RecursiveParserWrapper p = new RecursiveParserWrapper(new AutoDetectParser());
        ParseContext context = new ParseContext();
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractInlineImages(true);
        config.setExtractUniqueInlineImagesOnly(false);
        context.set(PDFParserConfig.class, config);
        context.set(Parser.class, p);

        try (InputStream stream = getInputStream("/parser/pdf/jbig2.pdf")) {
            p.parse(stream, h, new Metadata(), context);
        }
        List<Metadata> metadatas = h.getMetadataList();

        Assertions.assertNull(metadatas.get(0).get("X-TIKA:EXCEPTION:warn"),
                "Exception found: " + metadatas.get(0).get(
                        "X-TIKA:EXCEPTION:warn"));
        Assertions.assertEquals("91", metadatas.get(1).get("height"),
                "Invalid height.");
        Assertions.assertEquals("352", metadatas.get(1).get("width"),
                "Invalid width.");

//        System.out.println("OUTPUT:" + output);
//        System.out.println("METADATA:" + metadatas.get(1));

    }
}
