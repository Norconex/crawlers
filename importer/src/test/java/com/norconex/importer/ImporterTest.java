/* Copyright 2010-2022 Norconex Inc.
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
package com.norconex.importer;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.handler.filter.impl.TextFilter;
import com.norconex.importer.handler.transformer.DocumentTransformer;
import com.norconex.importer.parser.DocumentParserException;
import com.norconex.importer.parser.GenericDocumentParserFactory;
import com.norconex.importer.response.ImporterStatus;

class ImporterTest {

    @TempDir
    private Path tempDir;

    private Importer importer;

    @BeforeEach
    void setUp() throws Exception {
        var config = new ImporterConfig();
        config.setPostParseConsumer(HandlerConsumer.fromHandlers(
                (DocumentTransformer) (
                doc, input, output, parseState) -> {
            try {
               // Clean up what we know is extra noise for a given format
               var pattern = Pattern.compile("[^a-zA-Z ]");
               var txt = IOUtils.toString(
                input, StandardCharsets.UTF_8);
               txt = pattern.matcher(txt).replaceAll("");
               txt = txt.replaceAll("DowntheRabbitHole", "");
               txt = StringUtils.replace(txt, " ", "");
               txt = StringUtils.replace(txt, "httppdfreebooksorg", "");
               IOUtils.write(txt, output, StandardCharsets.UTF_8);
            } catch (IOException e) {
               throw new ImporterHandlerException(e);
            }
        }));
        importer = new Importer(config);
    }

    @AfterEach
    void tearDown() throws Exception {
        importer = null;
    }

    @Test
    void testImporter() throws IOException {
        // test that it works with empty contructor
        try (var is = getClass().getResourceAsStream(
                "/parser/msoffice/word.docx")) {
            Assertions.assertEquals("Hey Norconex, this is a test.",
                    TestUtil.toString(new Importer().importDocument(
                            new ImporterRequest(is))
                                    .getDocument().getInputStream()).trim());
        }
    }

    @Test
    void testMain() throws IOException {
        var in = TestUtil.getAliceHtmlFile().getAbsolutePath();
        var out =  tempDir.resolve("out.txt").toAbsolutePath().toString();
        Importer.main(new String[]{"-i", in, "-o", out});
        Assertions.assertTrue(FileUtils.readFileToString(
                new File(out), UTF_8).contains("And so it was indeed"));
    }


    @Test
    void testGetters() {
        Assertions.assertSame(importer, Importer.get());
        Assertions.assertNotNull(
                importer.getImporterConfig().getPostParseConsumer());
        Assertions.assertNotNull(importer.getEventManager());
    }

    @Test
    void testExceptions() throws IOException {
        // Invalid files
        Assertions.assertEquals(importer.importDocument(new ImporterRequest(
                tempDir.resolve("I-do-not-exist")))
                        .getImporterStatus().getException().getClass(),
                                ImporterException.class);

        Assertions.assertEquals(importer.importDocument(
                new Doc("ref", TestUtil.failingCachedInputStream()))
                        .getImporterStatus().getException().getClass(),
                                ImporterException.class);
    }

    @Test
    void testImportDocument() throws IOException {

        // MS Doc
        var docxOutput = File.createTempFile("ImporterTest-doc-", ".txt");
        var metaDocx = new Properties();
        writeToFile(importer.importDocument(
                new ImporterRequest(TestUtil.getAliceDocxFile().toPath())
                        .setMetadata(metaDocx)).getDocument(),
                        docxOutput);

        // PDF
        var pdfOutput = File.createTempFile("ImporterTest-pdf-", ".txt");
        var metaPdf = new Properties();
        writeToFile(importer.importDocument(
                new ImporterRequest(TestUtil.getAlicePdfFile().toPath())
                        .setMetadata(metaPdf)).getDocument(), pdfOutput);

        // ZIP/RTF
        var rtfOutput = File.createTempFile("ImporterTest-zip-rtf-", ".txt");
        var metaRtf = new Properties();
        writeToFile(importer.importDocument(
                new ImporterRequest(TestUtil.getAliceZipFile().toPath())
                        .setMetadata(metaRtf)).getDocument(), rtfOutput);

        double doc = docxOutput.length();
        double pdf = pdfOutput.length();
        double rtf = rtfOutput.length();
        if (Math.abs(pdf - doc) / 1024.0 > 0.03
                || Math.abs(pdf - rtf) / 1024.0 > 0.03) {
            Assertions.fail("Content extracted from examples documents are too "
                    + "different from each other. They were not deleted to "
                    + "help you troubleshoot under: "
                    + FileUtils.getTempDirectoryPath() + "ImporterTest-*");
        } else {
            FileUtils.deleteQuietly(docxOutput);
            FileUtils.deleteQuietly(pdfOutput);
            FileUtils.deleteQuietly(rtfOutput);
        }

        Assertions.assertTrue(pdfOutput.length() < 10,
                "Converted file size is too small to be valid.");
    }

    @Test
    void testNested() throws IOException {
        var config = new ImporterConfig();
        var parser = new GenericDocumentParserFactory();
        parser.getParseHints().getEmbeddedConfig().setSplitContentTypes(".*");
        config.setParserFactory(parser);

        var imp = new Importer(config);
        var resp = imp.importDocument(
                new ImporterRequest(TestUtil.getAliceZipFile().toPath())
                        .setMetadata(new Properties()));
        Assertions.assertNotNull(resp.getDocument());
        Assertions.assertNotNull(resp.getNestedResponses()[0]);
    }

    @Test
    void testImportRejected() {
        var config = new ImporterConfig();
        config.setPostParseConsumer(
                HandlerConsumer.fromHandlers(new TextFilter(
                TextMatcher.basic("Content-Type").setPartial(true),
                TextMatcher.basic("application/pdf").setPartial(true),
                OnMatch.EXCLUDE)));
        var importer = new Importer(config);
        var result = importer.importDocument(
                new ImporterRequest(TestUtil.getAlicePdfFile().toPath())
                        .setContentType(ContentType.PDF)
                        .setReference("n/a"));

        Assertions.assertTrue(result.getImporterStatus().isRejected()
                && result.getImporterStatus().getDescription().contains(
                        "TextFilter"),
                "PDF should have been rejected with proper "
                        + "status description.");
    }

    @Test
    void testResponseProcessor() {
        var config = new ImporterConfig();
        config.setResponseProcessors(Arrays.asList(
                res -> {
                    res.setImporterStatus(new ImporterStatus(
                        ImporterStatus.Status.ERROR, "test"));
                    return null;
                }));
        var imp = new Importer(config);
        var resp = imp.importDocument(
                new ImporterRequest(TestUtil.getAliceZipFile().toPath())
                        .setMetadata(new Properties()));
        Assertions.assertEquals(
                "test", resp.getImporterStatus().getDescription());
    }

    @Test
    void testSaveParseError() throws IOException {
        var config = new ImporterConfig();
        var errorDir = tempDir.resolve("errors");
        Files.createDirectories(errorDir);
        config.setParseErrorsSaveDir(errorDir);
        config.setParserFactory((documentReference, contentType) ->
            (doc, output) -> {
                throw new DocumentParserException("TEST");
         });

        var importer = new Importer(config);
        // test that it works with empty contructor
        try (var is = getClass().getResourceAsStream(
                "/parser/msoffice/word.docx")) {
            importer.importDocument(new ImporterRequest(is));
            // 1: *-content.docx;  2: -error.txt;  3: -meta.txt
            Assertions.assertEquals(3, Files.list(errorDir).count());
        }
    }

//
//    @Test
//    void testValidation() throws IOException {
//        var is = getClass().getResourceAsStream(
//                "/validation/importer-full.xml");
//        try (Reader r = new InputStreamReader(is)) {
//            Assertions.assertEquals(0,
//                    new XML(r).validate(ImporterConfig.class).size());
//        }
//    }

    private void writeToFile(Doc doc, File file)
            throws IOException {
        var out = new FileOutputStream(file);
        IOUtils.copy(doc.getInputStream(), out);
        out.close();
    }
}
