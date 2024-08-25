/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.importer.handler.parser.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ENGLISH;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.commons.io.IOUtils;
import org.apache.cxf.common.i18n.UncheckedException;
import org.assertj.core.api.Assertions;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterRequest;
import com.norconex.importer.response.ImporterResponse;

import lombok.NonNull;

public class ParseAssertions {

    private final ImporterResponse responseForFile;
    private final ImporterResponse responseForStream;
    private final ImporterResponse responseProvided;

    private ParseAssertions(
            @NonNull ImporterResponse responseForFile,
            @NonNull ImporterResponse responseForStream
    ) {
        this.responseForFile = responseForFile;
        this.responseForStream = responseForStream;
        responseProvided = null;
    }

    private ParseAssertions(@NonNull ImporterResponse responseProvided) {
        responseForFile = null;
        responseForStream = null;
        this.responseProvided = responseProvided;
    }

    public static ParseAssertions assertThat(Path file) {
        return doAssertThat(file, false);
    }

    public static ParseAssertions assertThatSplitted(Path file) {
        return doAssertThat(file, true);
    }

    public static ParseAssertions assertThat(ImporterResponse response) {
        return new ParseAssertions(response);
    }

    private static ParseAssertions doAssertThat(
            Path file, boolean split
    ) {
        Properties metadata;
        var config = new ImporterConfig();
        if (split) {
            config.setHandlers(
                    List.of(
                            Configurable.configure(
                                    new DefaultParser(),
                                    cfg -> cfg.getEmbeddedConfig()
                                            .setSplitContentTypes(
                                                    List.of(
                                                            TextMatcher
                                                                    .regex(".*")
                                                    )
                                            )
                            )
                    )
            );
        }

        // File-based parse
        metadata = new Properties();
        var responseForFile = new Importer(config).importDocument(
                new ImporterRequest(file).setMetadata(metadata)
        );
        //        doc = response.getDoc();
        //        assertDefaults(doc, "FILE",
        //                resourcePath, contentType, contentRegex, extension, family);
        //        responses[0] = response;

        // Input stream parse
        ImporterResponse responseForStream = null;
        try (var is = Files.newInputStream(file)) {
            metadata = new Properties();
            responseForStream = new Importer(config).importDocument(
                    new ImporterRequest(is)
                            .setMetadata(metadata)
                            .setReference("guess")
            );
        } catch (IOException e) {
            throw new UncheckedException(e);
        }
        //        doc = response.getDocument();
        //        assertDefaults(doc, "STREAM",
        //                resourcePath, contentType, contentRegex, extension, family);
        //        responses[1] = response;
        return new ParseAssertions(responseForFile, responseForStream);
    }

    public ParseAssertions hasContentType(String contentType) {
        return assertResponses(
                resp -> Assertions.assertThat(
                        resp.getDoc().getDocContext().getContentType()
                ).hasToString(
                        contentType
                )
        );
    }

    public ParseAssertions hasContentFamily(String contentFamily) {
        return assertResponses(
                resp -> Assertions.assertThat(
                        resp.getDoc().getDocContext().getContentType()
                                .getContentFamily()
                                .getDisplayName(ENGLISH)
                )
                        .hasToString(contentFamily)
        );
    }

    public ParseAssertions hasExtension(String extension) {
        return assertResponses(
                resp -> Assertions.assertThat(
                        resp.getDoc().getDocContext().getContentType()
                                .getExtension()
                )
                        .hasToString(extension)
        );
    }

    public ParseAssertions hasMetaValue(String field, String value) {
        return assertResponses(
                resp -> Assertions.assertThat(
                        resp.getDoc().getMetadata().getStrings(field)
                ).contains(value)
        );
    }

    public ParseAssertions hasMetaValuesCount(String field, int count) {
        return assertResponses(
                resp -> Assertions.assertThat(
                        resp.getDoc().getMetadata().getStrings(field)
                ).hasSize(count)
        );
    }

    public ParseAssertions contains(String text) {
        return assertResponses(
                resp -> Assertions
                        .assertThat(contentAsString(resp)).contains(text)
        );
    }

    public ParseAssertions doesNotContain(String text) {
        return assertResponses(
                resp -> Assertions.assertThat(
                        contentAsString(resp)
                ).doesNotContain(text)
        );
    }

    public ParseAssertions matches(TextMatcher matcher) {
        return assertResponses(
                resp -> Assertions.assertThat(
                        contentAsString(resp)
                ).matches(matcher.toRegexPattern())
        );
    }

    public ParseAssertions hasValidResponse(
            Predicate<ImporterResponse> predicate
    ) {
        return assertResponses(
                resp -> Assertions.assertThat(resp)
                        .matches(predicate)
        );
    }

    private ParseAssertions assertResponses(Consumer<ImporterResponse> c) {
        if (responseForFile != null) {
            c.accept(responseForFile);
        }
        if (responseForStream != null) {
            c.accept(responseForStream);
        }
        if (responseProvided != null) {
            c.accept(responseProvided);
        }
        return this;
    }

    private String contentAsString(ImporterResponse resp) {
        try {
            return IOUtils.toString(resp.getDoc().getInputStream(), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    //
    //    private void assertDefaults(
    //            Doc doc,
    //            String testType,
    //            String resourcePath,
    //            String contentType,
    //            String contentRegex,
    //            String extension,
    //            String family) throws IOException {
    //        var p = Pattern.compile(contentRegex, Pattern.DOTALL);
    //
    //        Assertions.assertNotNull(doc, "Document is null");
    //
    //        var content =
    //                IOUtils.toString(doc.getInputStream(), StandardCharsets.UTF_8);
    //        Assertions.assertEquals(ContentType.valueOf(contentType),
    //                doc.getDocRecord().getContentType(),
    //                testType + " content-type detection failed for \""
    //                        + resourcePath + "\".");
    //
    //        Assertions.assertTrue(p.matcher(content).find(),
    //                testType + " content extraction failed for \""
    //                        + resourcePath + "\". Content:\n" + content);
    //
    //        var ext = doc.getDocRecord().getContentType().getExtension();
    //        Assertions.assertEquals(extension, ext,
    //                testType + " extension detection failed for \""
    //                        + resourcePath + "\".");
    //
    //        var familyEnglish = doc.getDocRecord().getContentType()
    //                .getContentFamily().getDisplayName(Locale.ENGLISH);
    //        Assertions.assertEquals(family, familyEnglish,
    //                testType + " family detection failed for \""
    //                        + resourcePath + "\".");
    //    }
}
