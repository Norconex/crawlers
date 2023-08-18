/* Copyright 2020-2022 Norconex Inc.
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
package com.norconex.importer.handler.filter.impl;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.text.TextMatcher.Method;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterRequest;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.response.ImporterStatus.Status;

class TextFilterTest {

    @Test
    void testRegexContentMatchesExclude()
            throws ImporterHandlerException {
        var filter = newRegexTextFilter();
        filter.getValueMatcher().setPattern(".*string.*");
        filter.setOnMatch(OnMatch.EXCLUDE);
        Assertions.assertFalse(TestUtil.filter(filter, "n/a",
                IOUtils.toInputStream("a string that matches",
                        StandardCharsets.UTF_8), null, ParseState.PRE),
                "Should have been rejected.");
    }
    @Test
    void testRegexContentMatchesInclude()
            throws ImporterHandlerException {
        var filter = newRegexTextFilter();
        filter.getValueMatcher().setPattern(".*string.*");
        filter.setOnMatch(OnMatch.INCLUDE);
        Assertions.assertTrue(TestUtil.filter(filter, "n/a",
                IOUtils.toInputStream(
                        "a string that matches", StandardCharsets.UTF_8),
                        null, ParseState.PRE),
                "Should have been accepted.");
    }
    @Test
    void testRegexContentNoMatchesExclude()
            throws ImporterHandlerException {
        var filter = newRegexTextFilter();
        filter.getValueMatcher().setPattern(".*string.*");
        filter.setOnMatch(OnMatch.EXCLUDE);
        Assertions.assertTrue(
                TestUtil.filter(filter, "n/a", IOUtils.toInputStream(
                        "a text that does not match", StandardCharsets.UTF_8),
                        null, ParseState.PRE),
                "Should have been accepted.");
    }
    @Test
    void testRegexContentNoMatchesUniqueInclude()
            throws ImporterHandlerException {

        var filter = newRegexTextFilter();
        filter.getValueMatcher().setPattern(".*string.*");
        filter.setOnMatch(OnMatch.INCLUDE);
        Assertions.assertFalse(
                TestUtil.filter(filter, "n/a", IOUtils.toInputStream(
                        "a text that does not match", StandardCharsets.UTF_8),
                        null, ParseState.PRE),
                "Should have been rejected.");
    }

    @Test
    void testRegexContentMatchesOneOfManyIncludes() throws IOException {
        var filter1 = newRegexTextFilter();
        filter1.getValueMatcher().setPattern(".*string.*");
        filter1.setOnMatch(OnMatch.INCLUDE);

        var filter2 = newRegexTextFilter();
        filter2.getValueMatcher().setPattern(".*asdf.*");
        filter2.setOnMatch(OnMatch.INCLUDE);

        var filter3 = newRegexTextFilter();
        filter3.getValueMatcher().setPattern(".*qwer.*");
        filter3.setOnMatch(OnMatch.INCLUDE);

        var config = new ImporterConfig();
        config.setPreParseConsumer(
                HandlerConsumer.fromHandlers(filter1, filter2, filter3));

        var response = new Importer(config).importDocument(
                new ImporterRequest(
                        ReaderInputStream.builder()
                            .setCharset(StandardCharsets.UTF_8)
                            .setCharSequence("a string that matches")
                            .get())
                .setReference("N/A"));
        Assertions.assertEquals(
                Status.SUCCESS, response.getImporterStatus().getStatus(),
                "Status should have been SUCCESS");
    }

    @Test
    void testRegexContentNoMatchesOfManyIncludes() throws IOException {

        var filter1 = newRegexTextFilter();
        filter1.getValueMatcher().setPattern(".*zxcv.*");
        filter1.setOnMatch(OnMatch.INCLUDE);

        var filter2 = newRegexTextFilter();
        filter2.getValueMatcher().setPattern(".*asdf.*");
        filter2.setOnMatch(OnMatch.INCLUDE);

        var filter3 = newRegexTextFilter();
        filter3.getValueMatcher().setPattern(".*qwer.*");
        filter3.setOnMatch(OnMatch.INCLUDE);

        var config = new ImporterConfig();
        config.setPostParseConsumer(
                HandlerConsumer.fromHandlers(filter1, filter2, filter3));

        var response = new Importer(config).importDocument(
                new ImporterRequest(
                        ReaderInputStream.builder()
                        .setCharset(StandardCharsets.UTF_8)
                        .setCharSequence("no matches")
                        .get())
                .setReference("N/A"));
        Assertions.assertEquals(
                Status.REJECTED, response.getImporterStatus().getStatus(),
                "Status should have been REJECTED");
    }

    @Test
    void testRegexFieldDocument()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("field1", "a string to match");
        meta.add("field2", "something we want");

        var filter = newRegexTextFilter();

        filter.getFieldMatcher().setPattern("field1");
        filter.getValueMatcher().setPattern(".*string.*");
        filter.setOnMatch(OnMatch.EXCLUDE);

        Assertions.assertFalse(
                TestUtil.filter(filter, "n/a", null, meta, ParseState.PRE),
                "field1 not filtered properly.");

        filter.getFieldMatcher().setPattern("field2");
        Assertions.assertTrue(
                TestUtil.filter(filter, "n/a", null, meta, ParseState.PRE),
                "field2 not filtered properly.");
    }

    @Test
    void testWriteRead() {
        var filter = new TextFilter(new TextMatcher()
                .setMethod(Method.REGEX)
                .setPartial(true)
                .setPattern("blah"));
        filter.setFieldMatcher(new TextMatcher()
                .setMethod(Method.REGEX)
                .setPartial(true));
        XML.assertWriteRead(filter, "handler");
    }

    @Test
    void testConstructors() {
        assertThatNoException().isThrownBy(() -> {
            new TextFilter(null, (OnMatch) null);
            new TextFilter(null, null, null);
        });
    }

    private TextFilter newRegexTextFilter() {
        return new TextFilter(newRegexMatcher(), newRegexMatcher());
    }
    private TextMatcher newRegexMatcher() {
        return new TextMatcher().setMethod(Method.REGEX);
    }
}
