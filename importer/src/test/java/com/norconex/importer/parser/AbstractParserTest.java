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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterRequest;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.response.ImporterResponse;

public abstract class AbstractParserTest {

    public static final String DEFAULT_CONTENT_REGEX =
            "Hey Norconex, this is a test\\.";

    @TempDir
    static Path folder;

    protected File getFile(String resourcePath) throws IOException {
        var file = Files.createTempFile(folder, null,
                StringUtils.substringAfterLast(resourcePath, "/")).toFile();
        FileUtils.copyInputStreamToFile(getInputStream(resourcePath), file);
        return file;
    }

    protected InputStream getInputStream(String resourcePath) {
        return getClass().getResourceAsStream(resourcePath);
    }

    protected ImporterResponse[] testParsing(
            String resourcePath, String contentType,
            String contentRegex, String extension, String family)
                    throws IOException {
        return testParsing(resourcePath, contentType,
                contentRegex, extension, family, false);
    }
    protected ImporterResponse[] testParsing(
            String resourcePath, String contentType,
            String contentRegex, String extension, String family,
            boolean splitEmbedded) throws IOException {

        var responses = new ImporterResponse[2];

        Properties metadata;
        ImporterResponse response;
        Doc doc;
        var config = new ImporterConfig();
        if (splitEmbedded) {
            config.getParseConfig()
                .getParseOptions()
                .getEmbeddedConfig()
                .setSplitEmbeddedOf(List.of(TextMatcher.regex(".*")));
        }

        // Test file
        metadata = new Properties();
        response = new Importer(config).importDocument(
                new ImporterRequest(getFile(resourcePath).toPath())
                        .setMetadata(metadata));
        doc = response.getDocument();

        assertDefaults(doc, "FILE",
                resourcePath, contentType, contentRegex, extension, family);
        responses[0] = response;

        // Test input stream
        metadata = new Properties();
        response = new Importer(config).importDocument(
                new ImporterRequest(getInputStream(resourcePath))
                        .setMetadata(metadata)
                        .setReference("guess"));
        doc = response.getDocument();
        assertDefaults(doc, "STREAM",
                resourcePath, contentType, contentRegex, extension, family);
        responses[1] = response;
        return responses;
    }

    private void assertDefaults(
            Doc doc,
            String testType,
            String resourcePath,
            String contentType,
            String contentRegex,
            String extension,
            String family) throws IOException {
        var p = Pattern.compile(contentRegex, Pattern.DOTALL);

        Assertions.assertNotNull(doc, "Document is null");

        var content =
                IOUtils.toString(doc.getInputStream(), StandardCharsets.UTF_8);
        Assertions.assertEquals(ContentType.valueOf(contentType),
                doc.getDocRecord().getContentType(),
                testType + " content-type detection failed for \""
                        + resourcePath + "\".");

        Assertions.assertTrue(p.matcher(content).find(),
                testType + " content extraction failed for \""
                        + resourcePath + "\". Content:\n" + content);

        var ext = doc.getDocRecord().getContentType().getExtension();
        Assertions.assertEquals(extension, ext,
                testType + " extension detection failed for \""
                        + resourcePath + "\".");

        var familyEnglish = doc.getDocRecord().getContentType()
                .getContentFamily().getDisplayName(Locale.ENGLISH);
        Assertions.assertEquals(family, familyEnglish,
                testType + " family detection failed for \""
                        + resourcePath + "\".");
    }
}
