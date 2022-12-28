/* Copyright 2015-2022 Norconex Inc.
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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterRequest;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.ScriptRunner;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.response.ImporterStatus.Status;

class ScriptFilterTest {

    @ParameterizedTest
    @ArgumentsSource(SimpleProvider.class)
    void testScriptFilter(String engineName, String script)
            throws ImporterHandlerException, IOException {
        var filter = new ScriptFilter(new ScriptRunner<>(engineName, script));

        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));
        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        var returnValue = filter.acceptDocument(
                TestUtil.newHandlerDoc(htmlFile.getAbsolutePath(), is, metadata),
                is, ParseState.PRE);
        is.close();
        Assertions.assertTrue(returnValue);
    }

    static class SimpleProvider implements ArgumentsProvider {
        @Override
        public Stream<Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of(ScriptRunner.JAVASCRIPT_ENGINE, """
                    returnValue = metadata.getString('character') == 'Alice'
                        || content.indexOf('Alice') > -1;
                    """),
                Arguments.of(ScriptRunner.LUA_ENGINE, """
                    returnValue = metadata:getString('character') == 'Alice'
                        or content:find('Alice') ~= nil;
                    """),
//                Arguments.of(ScriptRunner.PYTHON_ENGINE, """
//                    returnValue = metadata.getString('character') == 'Alice' \
//                        or content.__contains__('Alice');
//                    """),
                Arguments.of(ScriptRunner.VELOCITY_ENGINE, """
                    #set($returnValue =
                        $metadata.getString("character") == "Alice"
                            || $content.contains("Alice"))
                    """)
            );
        }
    }

    @Test
    void testWriteRead() {
        XML.assertWriteRead(new ScriptFilter(
                new ScriptRunner<>(ScriptRunner.JAVASCRIPT_ENGINE,
                        "returnValue = blah == 'blah';")), "handler");
    }

    // Relates to: https://github.com/Norconex/importer/issues/86
    @Test
    void testPrePostScriptFilter() throws IOException {
        try (var r = ResourceLoader.getXmlReader(getClass())) {
            var cfg = new ImporterConfig();
            cfg.loadFromXML(new XML(r));
            var importer = new Importer(cfg);
            var resp = importer.importDocument(new ImporterRequest(
                    new ByteArrayInputStream("test".getBytes()))
                    .setReference("N/A"));
            var doc = resp.getDocument();
            Assertions.assertNotNull(doc, "Document must not be null");
            var status = resp.getImporterStatus().getStatus();
            Assertions.assertEquals(Status.SUCCESS, status);
        }
    }
}
