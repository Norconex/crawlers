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
package com.norconex.importer.handler.tagger.impl;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.ScriptRunner;
import com.norconex.importer.parser.ParseState;

class ScriptTaggerTest {

    @ParameterizedTest
    @ArgumentsSource(ContentModifyScriptProvider.class)
    void testScriptTagger(String engineName, String script)
            throws ImporterHandlerException, IOException {

        var t = new ScriptTagger(new ScriptRunner<>(engineName, script));
        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");

        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));

        t.tagDocument(TestUtil.newHandlerDoc(
                htmlFile.getAbsolutePath(), is, metadata), is, ParseState.PRE);

        is.close();

        var successField = metadata.getString("test");
        var storyField = metadata.getString("story");

        Assertions.assertEquals("success", successField);
        Assertions.assertEquals(0,
                StringUtils.countMatches(storyField, "Alice"));
        Assertions.assertEquals(34,
                StringUtils.countMatches(storyField, "Roger"));
    }

    static class ContentModifyScriptProvider implements ArgumentsProvider {
        @Override
        public Stream<Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of(ScriptRunner.JAVASCRIPT_ENGINE, """
                    metadata.add('test', 'success');
                    var story = content.replace(/Alice/g, 'Roger');
                    metadata.add('story', story);
                    """),
                Arguments.of(ScriptRunner.LUA_ENGINE, """
                    metadata:add('test', {'success'});
                    local story = content:gsub('Alice', 'Roger');
                    metadata:add('story', {story});
                    """),
//                Arguments.of(ScriptRunner.PYTHON_ENGINE, """
//                    metadata.add('test', 'success')
//                    story = content.replace('Alice', 'Roger')
//                    metadata.add('story', story);
//                    """),
                Arguments.of(ScriptRunner.VELOCITY_ENGINE, """
                    $metadata.add("test", "success")
                    #set($story = $content.replace('Alice', 'Roger'))
                    $metadata.add("story", $story)
                    """)
            );
        }
    }

    @Test
    void testWriteRead() {
        XML.assertWriteRead(new ScriptTagger(
                new ScriptRunner<>(ScriptRunner.JAVASCRIPT_ENGINE,
                        "var blah = 'blah';")), "handler");
    }
}
