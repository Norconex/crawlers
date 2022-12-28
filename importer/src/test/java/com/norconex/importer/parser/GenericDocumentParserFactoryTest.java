/* Copyright 2014-2022 Norconex Inc.
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
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterRequest;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.parser.impl.ExternalParser;

public class GenericDocumentParserFactoryTest {

    @Test
    public void testWriteRead() {
        GenericDocumentParserFactory f = new GenericDocumentParserFactory();

        // default read/write
//        XMLConfigurationUtil.assertWriteRead(f);

        // more complex read/write
        f.setIgnoredContentTypesRegex("test");
        EmbeddedConfig emb = f.getParseHints().getEmbeddedConfig();
        emb.setNoExtractContainerContentTypes("noExtractContainerTest");
        emb.setNoExtractEmbeddedContentTypes("noExtractEmbeddedTest");
        emb.setSplitContentTypes(".*");

        OCRConfig ocr = f.getParseHints().getOcrConfig();
        ocr.setContentTypes("ocrContentTypesTest");
        ocr.setLanguages("ocrLanguages");
        ocr.setPath("ocrPath");

        ExternalParser app = new ExternalParser();
        app.setCommand("command.exe");
        f.registerParser(ContentType.BMP, app);
        XML.assertWriteRead(f, "documentParserFactory");
    }

    @Test
    public void testIgnoringContentTypes() throws IOException {

        GenericDocumentParserFactory factory =
                new GenericDocumentParserFactory();
        factory.setIgnoredContentTypesRegex("application/pdf");
        Properties metadata = new Properties();

        ImporterConfig config = new ImporterConfig();
        config.setParserFactory(factory);
        Importer importer = new Importer(config);
        Doc doc = importer.importDocument(
                new ImporterRequest(TestUtil.getAlicePdfFile().toPath())
                        .setContentType(ContentType.PDF)
                        .setMetadata(metadata)
                        .setReference("n/a")).getDocument();

        try (InputStream is = doc.getInputStream()) {
            String output = IOUtils.toString(
                    is, StandardCharsets.UTF_8).substring(0, 100);
            output = StringUtils.remove(output, '\n');
            Assertions.assertTrue(
                    !StringUtils.isAsciiPrintable(output),
                    "Non-parsed output expected to be binary.");
        }
    }
}
