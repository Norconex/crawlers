/* Copyright 2023 Norconex Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterRequest;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.parser.impl.ExternalParser;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

class ParseConfigTest {

    @Test
    void testIgnoringContentTypes() throws IOException {
        var config = new ImporterConfig();
        config.getParseConfig().setContentTypeExcludes(
                List.of(TextMatcher.regex("application/pdf")));

        var metadata = new Properties();
        var importer = new Importer(config);
        var doc = importer.importDocument(
                new ImporterRequest(TestUtil.getAlicePdfFile().toPath())
                        .setContentType(ContentType.PDF)
                        .setMetadata(metadata)
                        .setReference("n/a")).getDocument();

        try (InputStream is = doc.getInputStream()) {
            var output = IOUtils.toString(is, UTF_8).substring(0, 100);
            output = StringUtils.remove(output, '\n');
            Assertions.assertTrue(
                    !StringUtils.isAsciiPrintable(output),
                    "Non-parsed output expected to be binary.");
        }
    }

    @Test
    void testWriteRead() {
        var cfg = new ParseConfig();
        cfg.setParser(ContentType.HTML, new TestParser("htmlParser"));
        var app = new ExternalParser();
        app.setCommand("command.exe");
        cfg.setParser(ContentType.BMP, app);

        var pm = new PropertyMatchers();
        pm.add(new PropertyMatcher(TextMatcher.basic("pmBlah")));
        cfg.setContentTypeIncludes(List.of(
                TextMatcher.basic("incl1"),
                TextMatcher.basic("incl2")));
        cfg.setContentTypeExcludes(List.of(
                TextMatcher.basic("excl1"),
                TextMatcher.basic("excl2")));
        cfg.setDefaultParser(new TestParser("defaultParser"));
        cfg.setErrorsSaveDir(Path.of("/tmp/someErrorDir"));
        cfg.getParseOptions().setOptions(Map.of("k1", "v1", "k2", "v2"));

        cfg.getParseOptions().getEmbeddedConfig().setSkipEmmbbededOf(List.of(
                TextMatcher.basic("skip1")));
        cfg.getParseOptions().getOcrConfig().setTesseractPath(
                Path.of("/tmp/somePath"));

        assertThatNoException().isThrownBy(() -> XML.assertWriteRead(
                cfg, "parse"));
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @XmlRootElement(name = "parser")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TestParser implements DocumentParser {
        private String name;
        @Override
        public void init(@NonNull ParseOptions parseOptions)
                throws DocumentParserException {
            //NOOP
        }
        @Override
        public List<Doc> parseDocument(Doc doc, Writer output)
                throws DocumentParserException {
            return null;
        }
    }
}
