/* Copyright 2014-2023 Norconex Inc.
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterRequest;
import com.norconex.importer.response.ImporterResponse;

class EmbeddedTest {

    private static final String ZIP = "application/zip";
    private static final String PPT = "application/vnd"
            + ".openxmlformats-officedocument.presentationml.presentation";
    private static final String XLS = "application/vnd"
            + ".openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PNG = "image/png";
    private static final String TXT = "text/plain";
    private static final String EMF = "image/emf";


    // temp folder is for embedded tests only. move embedded tests in own file?
    @TempDir
    static Path folder;


    @Test
    void testEmbeddedDefaultMerged() throws IOException {

        // Make sure merge works (extracting all embedded)

        var response = importFileZipFile(c -> {});

        // no split == no child responses
        Assertions.assertEquals(
                0, response.getNestedResponses().length,
                "Non-split parsing cannot have child responses.");

        // must have detected 5 content types:
        // 1 zip, which holds:
        //      1 PowerPoint file, which holds 1 excel and 1 picture (png)
        //      1 Plain text file
        String[] expectedTypes = { ZIP, PPT, EMF, XLS, PNG, TXT };
        var responseTypes = getTikaContentTypes(response);
        for (String type : expectedTypes) {
            Assertions.assertTrue(
                    responseTypes.contains(type), "Expected to find " + type);
        }

        // make sure spreadsheet is extracted
        var content = IOUtils.toString(
                response.getDocument().getInputStream(),
                StandardCharsets.UTF_8);
        Assertions.assertTrue(
                content.contains("column 1"), "Spreadsheet not extracted.");
    }

    @Test
    void testEmbeddedDefaultSplit() throws IOException {

        // Make sure split works (extracting all embedded)
        var zipResponse = importFileZipFile(
                ec -> ec.setSplitEmbeddedOf(List.of(TextMatcher.regex(".*"))));

        // split == 2 child responses, one of which has two or more more
        Assertions.assertEquals(
                2, zipResponse.getNestedResponses().length,
                "Zip must have two embedded docs.");

        var pptResponse = findResponse(zipResponse, PPT);
        Assertions.assertTrue(
                pptResponse.getNestedResponses().length >= 2,
                "PowerPoint must have at least two embedded docs.");

        // must have detected 5 content types:
        // 1 zip, which holds:
        //      1 PowerPoint file, which holds 1 excel and 1 picture (png)
        //      1 Plain text file
        String[] expectedTypes = { ZIP, PPT, EMF, XLS, PNG, TXT };
        var responseTypes = getTikaContentTypes(zipResponse);
        for (String type : expectedTypes) {
            Assertions.assertTrue(
                    responseTypes.contains(type),
                    "Expected to find " + type);
        }

        var xlsResponse = findResponse(pptResponse, XLS);
        var xlsContent = IOUtils.toString(
                xlsResponse.getDocument().getInputStream(),
                StandardCharsets.UTF_8);
        // make sure spreadsheet is extracted
        Assertions.assertTrue(
                xlsContent.contains("column 1"),
                "Spreadsheet not extracted.");
    }

    @Test
    void testEmbeddedSplitZipOnly() throws IOException {

        // Split embedded files in zip but no deeper (PowerPoint embedded files
        // must not be split).

        var zipResponse = importFileZipFile(
                ec -> ec.setSplitEmbeddedOf(List.of(TextMatcher.basic(ZIP))));

        // split == 2 child responses, with PowerPoint being merged (no child).
        Assertions.assertEquals(
                2, zipResponse.getNestedResponses().length,
                "Zip must have two embedded docs.");

        var pptResponse = findResponse(zipResponse, PPT);
        Assertions.assertEquals(
                0, pptResponse.getNestedResponses().length,
                "PowerPoint must not have any embedded docs.");

        // must NOT have detected PowerPoint embedded (XLS and PNG)
        Assertions.assertNull(
                findResponse(pptResponse, XLS),
                "Must not find Excel sheet response.");
        Assertions.assertNull(
                findResponse(pptResponse, PNG),
                "Must not find PNG inage response.");

        var pptContent = IOUtils.toString(
                pptResponse.getDocument().getInputStream(),
                StandardCharsets.UTF_8);
        // make sure spreadsheet is extracted as part of PowerPoint
        Assertions.assertTrue(
                pptContent.contains("column 1"),
                "Spreadsheet not extracted.");
    }

    @Test
    void testNoExtractContainerZipMerged() throws IOException {

        // Extract zip content, but not its embedded files.  So should just
        // get file names as zip content.
        var zipResponse = importFileZipFile(
                ec -> ec.setSkipEmmbbededOf(List.of(TextMatcher.basic(ZIP))));

        // must NOT have other content types, just zip.
        var responseTypes = getTikaContentTypes(zipResponse);
        Assertions.assertEquals(
                1, responseTypes.size(),
                "Should only have 1 content type.");

        // the only content type must be zip
        Assertions.assertEquals(ZIP, zipResponse.getDocument()
                .getDocRecord().getContentType().toString(),
                        "Must be zip content type.");

        var content = IOUtils.toString(
                zipResponse.getDocument().getInputStream(),
                StandardCharsets.UTF_8);

        // make sure spreadsheet content is NOT extracted
        Assertions.assertFalse(
                content.contains("column 1"),
                "Spreadsheet must not be extracted.");

        // make sure content contains file names
        Assertions.assertTrue(
                content.contains("embedded.pptx")
                && content.contains("embedded.txt"),
                "File names must be extracted.");
    }

    @Test
    void testNoExtractContainerZipSplit() throws IOException {

        // Extract zip content, but not its embedded files.  So should just
        // get file names as zip content.
        var zipResponse = importFileZipFile(ec -> {
            ec.setSkipEmmbbededOf(List.of(TextMatcher.basic(ZIP)));
            ec.setSplitEmbeddedOf(List.of(TextMatcher.regex(".*")));
        });

        // must NOT have other content types, just zip.
        Assertions.assertTrue(
                ArrayUtils.isEmpty(zipResponse.getNestedResponses()),
                "Should not have nested responses.");

        var responseTypes = getTikaContentTypes(zipResponse);
        Assertions.assertEquals(
                1, responseTypes.size(),
                "Should only have 1 content type.");
        // must NOT have detected PowerPoint or text file
        Assertions.assertNull(
                findResponse(zipResponse, PPT),
                "Must not find Excel sheet response.");
        Assertions.assertNull(
                findResponse(zipResponse, TXT),
                "Must not find Text response.");

        // the only content type must be zip
        Assertions.assertEquals(
                ZIP, zipResponse.getDocument()
                        .getDocRecord().getContentType().toString(),
                                "Must be zip content type.");

        var content = IOUtils.toString(
                zipResponse.getDocument().getInputStream(),
                StandardCharsets.UTF_8);

        // make sure spreadsheet content is NOT extracted
        Assertions.assertFalse(
                content.contains("column 1"),
                "Spreadsheet must not be extracted.");

        // make sure content contains file names
        Assertions.assertTrue(
                content.contains("embedded.pptx")
                && content.contains("embedded.txt"),
                "File names must be extracted.");
    }


    @Test
    void testNoExtractContainerPowerPointMerged() throws IOException {

        // Extract zip content and its embedded files, except for its
        // PowerPoint, which should not see its embedded files extracted.
        var zipResponse = importFileZipFile(
                ec -> ec.setSkipEmmbbededOf(List.of(TextMatcher.basic(PPT))));

        // must have only zip, ppt, and txt.
        var responseTypes = getTikaContentTypes(zipResponse);
        Assertions.assertEquals(
                3, responseTypes.size(),
                "Should only have 3 content typs.");

        // must have detected 3 content types:
        // 1 zip, which holds:
        //      1 PowerPoint file, which holds no embedded
        //      1 Plain text file
        String[] expectedTypes = { ZIP, PPT, TXT };
        for (String type : expectedTypes) {
            Assertions.assertTrue(responseTypes.contains(type),
                "Expected to find " + type);
        }

        // Must not contain XLS and PNG
        Assertions.assertFalse(
                responseTypes.contains(XLS), "Must not contain XLS.");
        Assertions.assertFalse(
                responseTypes.contains(PNG), "Must not contain PNG.");

        var content = IOUtils.toString(
                zipResponse.getDocument().getInputStream(),
                StandardCharsets.UTF_8);

        // make sure spreadsheet content is NOT extracted
        Assertions.assertFalse(
                content.contains("column 1"),
                "Spreadsheet must not be extracted.");

        // make sure PowerPoint was otherwise extracted
        Assertions.assertTrue(
                content.contains("Slide 1: Embedded Test"),
                "PowerPoint must be extracted.");
    }


    @Test
    void testNoExtractContainerPowerPointSplit() throws IOException {

        // Extract zip content and its embedded files, except for its
        // PowerPoint, which should not see its embedded files extracted.
        var zipResponse = importFileZipFile(ec -> {
            ec.setSkipEmmbbededOf(List.of(TextMatcher.basic(PPT)));
            ec.setSplitEmbeddedOf(List.of(TextMatcher.regex(".*")));
        });

        // must have only zip, ppt, and txt.
        var responseTypes = getTikaContentTypes(zipResponse);
        Assertions.assertEquals(
                3, responseTypes.size(),
                "Should only have 3 content typs.");

        // must have detected 3 content types:
        // 1 zip, which holds:
        //      1 PowerPoint file, which holds no embedded
        //      1 Plain text file
        String[] expectedTypes = { ZIP, PPT, TXT };
        for (String type : expectedTypes) {
            Assertions.assertTrue(
                    responseTypes.contains(type),
                "Expected to find " + type);
        }

        // Must not contain XLS and PNG
        Assertions.assertFalse(
                responseTypes.contains(XLS),
                "Must not contain XLS.");
        Assertions.assertFalse(
                responseTypes.contains(PNG),
                "Must not contain PNG.");

        // must have detected PowerPoint and text file
        Assertions.assertNotNull(
                findResponse(zipResponse, PPT),
                "Must have PowerPoint response.");
        Assertions.assertNotNull(
                findResponse(zipResponse, TXT),
                "Must have Text response.");

        // must NOT have detected PowerPoint embedded files
        Assertions.assertNull(
                findResponse(zipResponse, XLS),
                "Must not find Excel sheet response.");
        Assertions.assertNull(
                findResponse(zipResponse, PNG),
                "Must not find Image response.");


        var content = IOUtils.toString(
                zipResponse.getDocument().getInputStream(),
                StandardCharsets.UTF_8);

        // make sure spreadsheet content is NOT extracted
        Assertions.assertFalse(
                content.contains("column 1"),
                "Spreadsheet must not be extracted.");

        // make sure PowerPoint was otherwise extracted
        var pptResponse = findResponse(zipResponse, PPT);
        Assertions.assertTrue(
                IOUtils.toString(pptResponse.getDocument().getInputStream(),
                        StandardCharsets.UTF_8).contains(
                                "Slide 1: Embedded Test"),
                "PowerPoint must be extracted.");
    }


    @Test
    void testNoExtractEmbeddedExcelMerged() throws IOException {

        // Extract zip content and all its embedded except for its excel file.
        var zipResponse = importFileZipFile(ec -> ec.setSkipEmmbbeded(
                List.of(TextMatcher.regex("(" + EMF + "|" + XLS + ")"))));

        // must have all content types except for Excel.
        // 1 zip, which holds:
        //      1 PowerPoint file, which should have extracted PNG only
        //      1 Plain text file
        var responseTypes = getTikaContentTypes(zipResponse);

        String[] expectedTypes = { ZIP, PPT, PNG, TXT };
        for (String type : expectedTypes) {
            Assertions.assertTrue(
                    responseTypes.contains(type),
                "Expected to find " + type);
        }

        // Must not contain XLS and PNG
        Assertions.assertFalse(
                responseTypes.contains(XLS),
                "Must not contain XLS.");

        var content = IOUtils.toString(
                zipResponse.getDocument().getInputStream(),
                StandardCharsets.UTF_8);

        // make sure spreadsheet content is NOT extracted
        Assertions.assertFalse(
                content.contains("column 1"),
                "Spreadsheet must not be extracted.");
    }

    @Test
    void testNoExtractEmbeddedExcelSplit() throws IOException {

        // Extract zip content and all its embedded except for its excel file.
        var zipResponse = importFileZipFile(ec -> {
            ec.setSkipEmmbbeded(List.of(TextMatcher.basic(XLS)));
            ec.setSplitEmbeddedOf(List.of(TextMatcher.regex(".*")));
        });

        // must have all content types except for Excel.
        // 1 zip, which holds:
        //      1 PowerPoint file, which should have extracted PNG only
        //      1 Plain text file
        var responseTypes = getTikaContentTypes(zipResponse);

        String[] expectedTypes = { ZIP, PPT, PNG, TXT };
        for (String type : expectedTypes) {
            Assertions.assertTrue(
                    responseTypes.contains(type),
                "Expected to find " + type);
        }

        // Must not contain XLS and PNG
        Assertions.assertFalse(
                responseTypes.contains(XLS),
                "Must not contain XLS.");

        // must have detected PowerPoint, text file, and PNG
        Assertions.assertNotNull(
                findResponse(zipResponse, PPT),
                "Must have PowerPoint response.");
        Assertions.assertNotNull(
                findResponse(zipResponse, TXT),
                "Must have Text response.");
        Assertions.assertNotNull(
                findResponse(zipResponse, PNG),
                "Must have Image response.");

        // must NOT have detected PowerPoint embedded files
        Assertions.assertNull(
                findResponse(zipResponse, XLS),
                "Must not find Excel sheet response.");


        var content = IOUtils.toString(
                zipResponse.getDocument().getInputStream(),
                StandardCharsets.UTF_8);

        // make sure spreadsheet content is NOT extracted
        Assertions.assertFalse(
                content.contains("column 1"),
                "Spreadsheet must not be extracted.");
    }



    private ImporterResponse findResponse(
            ImporterResponse response, String contentType) {
        if (response.getDocument().getDocRecord()
                .getContentType().toString().equals(contentType)) {
            return response;
        }
        var childResponses = response.getNestedResponses();
        if (childResponses == null) {
            return null;
        }
        for (ImporterResponse childResponse : childResponses) {
            var foundResponse =
                    findResponse(childResponse, contentType);
            if (foundResponse != null) {
                return foundResponse;
            }
        }
        return null;
    }

    private List<String> getTikaContentTypes(ImporterResponse response) {
        List<String> types = new ArrayList<>();
        var meta = response.getDocument().getMetadata();
        var rawTypes = meta.getStrings("Content-Type");
        for (String t : rawTypes) {
            types.add(StringUtils.substringBefore(t, ";"));
        }
        var nestedResponses = response.getNestedResponses();
        for (ImporterResponse nr : nestedResponses) {
            types.addAll(getTikaContentTypes(nr));
        }
        return types;
    }

    private ImporterResponse importFileZipFile(Consumer<EmbeddedConfig> embdCfg)
            throws IOException {

        var metadata = new Properties();
        var config = new ImporterConfig();
        embdCfg.accept(
                config.getParseConfig().getParseOptions().getEmbeddedConfig());
        var importer = new Importer(config);
        return importer.importDocument(
                new ImporterRequest(getZipFile().toPath())
                        .setMetadata(metadata));
    }

    private File getZipFile() throws IOException {
        var is = getClass().getResourceAsStream(
                "/parser/embedded/embedded.zip");
        var file = folder.resolve("test-embedded.zip").toFile();
        FileUtils.copyInputStreamToFile(is, file);
        is.close();
        return file;
    }
}
