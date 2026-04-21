/* Copyright 2010-2026 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Strings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.parser.impl.DefaultParser;

@Timeout(30)
class ImporterTest {

    @TempDir
    private Path tempDir;

    private Importer importer;

    @BeforeEach
    void setUp() throws Exception {
        var config = new ImporterConfig();

        config.setHandlers(List.of(
                Configurable.configure(new DefaultParser(), cfg -> {
                    cfg.getEmbeddedConfig()
                            .setSplitContentTypes(List.of(
                                    TextMatcher.wildcard("*zip")))
                            .setSkipEmbeddedContentTypes(List.of(
                                    TextMatcher.wildcard("*jpeg"),
                                    TextMatcher.wildcard("*wmf")));

                }),
                ctx -> {
                    try {
                        ctx.output().asWriter().write(
                                normalizeExtractedText(
                                        ctx.input().asString()));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return true;
                }));
        importer = new Importer(config);
    }

    @AfterEach
    void tearDown() {
        importer = null;
    }

    @Test
    void testImporter() throws IOException {
        // test that it works with empty contructor
        try (var imp = new Importer();
                var is = getClass().getResourceAsStream(
                        "/parser/msoffice/word.docx")) {
            Assertions.assertEquals(
                    "Hey Norconex, this is a test.",
                    TestUtil.toString(
                            imp.importDocument(
                                    new ImporterRequest(is)).getDoc()
                                    .getInputStream())
                            .trim());
        }
    }

    @Test
    void testMain() throws IOException {
        var inFile = TestUtil.getAliceHtmlFile().getAbsolutePath();
        var outFile = tempDir.resolve("out.txt").toAbsolutePath().toString();
        Importer.main(new String[] { "-i", inFile, "-o", outFile });
        Assertions.assertTrue(
                FileUtils.readFileToString(
                        new File(outFile), UTF_8)
                        .contains("And so it was indeed"));
    }

    @Test
    void testGetters() {
        Assertions.assertSame(importer, Importer.get());
        Assertions.assertTrue(
                CollectionUtils.isNotEmpty(
                        importer.getConfiguration().getHandlers()));
        Assertions.assertNotNull(importer.getEventManager());
    }

    @SuppressWarnings("resource")
    @Test
    void testExceptions() throws IOException {
        // Invalid files
        Assertions.assertEquals(
                importer.importDocument(
                        new ImporterRequest(
                                tempDir.resolve("I-do-not-exist")))
                        .getException().getClass(),
                ImporterException.class);

        Assertions.assertEquals(
                importer.importDocument(
                        new Doc("ref").setInputStream(
                                TestUtil.failingCachedInputStream()))
                        .getException().getClass(),
                ImporterException.class);
    }

    @Test
    void testImportDocument() throws IOException {
        var metaDocx = new Properties();
        var docxContent = importedText(
                new ImporterRequest(TestUtil.getAliceDocxFile().toPath())
                        .setMetadata(metaDocx));

        var metaPdf = new Properties();
        var pdfContent = importedText(
                new ImporterRequest(TestUtil.getAlicePdfFile().toPath())
                        .setMetadata(metaPdf));

        var metaRtf = new Properties();
        var rtfResponse = importer.importDocument(
                new ImporterRequest(TestUtil.getAliceZipFile().toPath())
                        .setMetadata(metaRtf));
        var rtfContent = TestUtil.toString(
                rtfResponse.getNestedResponses().get(0)
                        .getDoc().getInputStream());

        var expectedContent = normalizeExtractedText(
                Files.readString(TestUtil.getAliceTextFile().toPath(), UTF_8));
        var earlyExcerpt = excerpt(expectedContent, 400, 120);
        var middleExcerpt = excerpt(
                expectedContent, expectedContent.length() / 2, 120);

        assertComparableToReference(
                docxContent, expectedContent, "DOCX", earlyExcerpt,
                middleExcerpt);
        assertComparableToReference(
                pdfContent, expectedContent, "PDF", earlyExcerpt,
                middleExcerpt);
        assertComparableToReference(
                rtfContent, expectedContent, "RTF", earlyExcerpt,
                middleExcerpt);
    }

    //TODO uncomment following to test rejections and validation

    //    @Test
    //    void testImportRejected() {
    //        var config = new ImporterConfig();
    //        config.
    //
    //        config.setPostParseConsumer(
    //                HandlerConsumerAdapter.fromHandlers(new TextFilter(
    //                TextMatcher.basic("Content-Type").setPartial(true),
    //                TextMatcher.basic("application/pdf").setPartial(true),
    //                OnMatch.EXCLUDE)));
    //        var importer = new Importer(config);
    //        var result = importer.importDocument(
    //                new ImporterRequest(TestUtil.getAlicePdfFile().toPath())
    //                        .setContentType(ContentType.PDF)
    //                        .setReference("n/a"));
    //
    //        Assertions.assertTrue(result.getImporterStatus().isRejected()
    //                && result.getImporterStatus().getDescription().contains(
    //                        "TextFilter"),
    //                "PDF should have been rejected with proper "
    //                        + "status description.");
    //    }
    //
    //    @Test
    //    void testResponseProcessor() {
    //        var config = new ImporterConfig();
    //        config.setResponseProcessors(Arrays.asList(
    //                res -> {
    //                    res.setImporterStatus(new ImporterStatus(
    //                        ImporterStatus.Status.ERROR, "test"));
    //                    return null;
    //                }));
    //        var imp = new Importer(config);
    //        var resp = imp.importDocument(
    //                new ImporterRequest(TestUtil.getAliceZipFile().toPath())
    //                        .setMetadata(new Properties()));
    //        Assertions.assertEquals(
    //                "test", resp.getImporterStatus().getDescription());
    //    }
    //
    //    @Test
    //    void testSaveParseError() throws IOException {
    //        var config = new ImporterConfig();
    //        var errorDir = tempDir.resolve("errors");
    //        Files.createDirectories(errorDir);
    //        config.getParseConfig().setErrorsSaveDir(errorDir);
    //        config.getParseConfig().setDefaultParser(new DocumentParser() {
    //            @Override
    //            public List<Doc> parseDocument(Doc doc, Writer output)
    //                    throws DocumentParserException {
    //                throw new DocumentParserException("TEST");
    //            }
    //            @Override
    //            public void init(@NonNull ParseOptions parseOptions)
    //                    throws DocumentParserException {
    //                //NOOP
    //            }
    //        });
    //
    //        var importer = new Importer(config);
    //        // test that it works with empty contructor
    //        try (var is = getClass().getResourceAsStream(
    //                "/parser/msoffice/word.docx")) {
    //            importer.importDocument(new ImporterRequest(is));
    //            // 1: *-content.docx;  2: -error.txt;  3: -meta.txt
    //            Assertions.assertEquals(3, Files.list(errorDir).count());
    //        }
    //    }
    //

    //TODO Finish migrating old config

    @Test
    void testFullConfiguration() throws IOException {
        try (Reader r = new InputStreamReader(
                getClass().getResourceAsStream(
                        "/validation/importer-full.yaml"))) {
            assertThatNoException().isThrownBy(() -> {
                var importer = new Importer(
                        BeanMapper.DEFAULT.read(
                                ImporterConfig.class, r, Format.YAML));
                BeanMapper.DEFAULT.assertWriteRead(importer);
            });
        }
    }

    private String importedText(ImporterRequest request) {
        return TestUtil.toString(importer.importDocument(request)
                .getDoc().getInputStream());
    }

    private void assertComparableToReference(
            String actual,
            String expected,
            String format,
            String earlyExcerpt,
            String middleExcerpt) {
        Assertions.assertTrue(
                actual.length() > 4_000,
                format + " extracted content is too small to be valid.");
        Assertions.assertTrue(
                relativeDifference(actual.length(), expected.length()) < 0.20,
                format + " extracted content length diverged too much from "
                        + "the reference text.");
        Assertions.assertTrue(
                actual.contains(earlyExcerpt),
                format + " extracted content is missing an early chapter "
                        + "excerpt.");
        Assertions.assertTrue(
                actual.contains(middleExcerpt),
                format + " extracted content is missing a middle chapter "
                        + "excerpt.");
    }

    private double relativeDifference(int actualLength, int expectedLength) {
        return Math.abs(actualLength - expectedLength)
                / (double) expectedLength;
    }

    private String excerpt(String text, int center, int length) {
        var safeLength = Math.min(length, text.length());
        var start = Math.max(0,
                Math.min(center - (safeLength / 2),
                        text.length() - safeLength));
        return text.substring(start, start + safeLength);
    }

    private String normalizeExtractedText(String text) {
        var pattern = Pattern.compile("[^a-zA-Z ]");
        var normalized = pattern.matcher(text).replaceAll("");
        normalized = normalized.replaceAll("DowntheRabbitHole", "");
        normalized = Strings.CS.replace(normalized, " ", "");
        normalized = Strings.CS.replace(
                normalized, "httppdfreebooksorg", "");
        normalized = Strings.CS.replace(normalized, "filejpg", "");
        normalized = Strings.CS.replace(normalized, "filewmf", "");
        return normalized;
    }
}
