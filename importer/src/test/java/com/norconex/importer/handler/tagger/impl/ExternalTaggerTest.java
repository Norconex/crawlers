/* Copyright 2017-2022 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.RegexFieldValueExtractor;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.ExternalApp;

class ExternalTaggerTest {

    static final String INPUT = "1 2 3\n4 5 6\n7 8 9";
    static final String EXPECTED_OUTPUT = "3 2 1\n6 5 4\n9 8 7";

    @Test
    void testWriteRead() {
        var t = new ExternalTagger();
        t.setCommand("my command");
        t.setInputDisabled(true);
        t.setTempDir(Paths.get("/some-path"));

        t.setMetadataInputFormat("json");
        t.setMetadataOutputFormat("xml");

        t.setMetadataExtractionPatterns(
            new RegexFieldValueExtractor("aaa.*", "111"),
            new RegexFieldValueExtractor("bbb.*", "222")
        );
        t.addMetadataExtractionPattern("ccc.*", "333");
        t.addMetadataExtractionPattern("ddd.*", "444", 2);


        Map<String, String> setEnvs = new HashMap<>();
        setEnvs.put("env1", "value1");
        setEnvs.put("env2", "value2");
        t.setEnvironmentVariables(setEnvs);
        Map<String, String> addEnvs = new HashMap<>();
        addEnvs.put("env3", "value3");
        addEnvs.put("env4", "value4");
        t.addEnvironmentVariables(addEnvs);
        t.addEnvironmentVariable("env5", "value5");

        assertThatNoException().isThrownBy(() ->
                XML.assertWriteRead(t, "handler"));


        assertThat(t.getCommand()).isEqualTo("my command");
        assertThat(t.getMetadataExtractionPatterns()).hasSize(4);
        assertThat(t.getEnvironmentVariables()).hasSize(5);
        assertThat(t.getMetadataOutputFormat()).isEqualTo("xml");
        assertThat(t.getMetadataInputFormat()).isEqualTo("json");
        assertThat(t.getOnSet()).isNull();
        assertThat(t.getTempDir().toString()).contains("some-path");
    }

    @Test
    void testMetaInputMetaOutput()
            throws ImporterHandlerException {
        testWithExternalApp("""
        	-ic ${INPUT}\s\
        	-im ${INPUT_META} -om ${OUTPUT_META}\s\
        	-ref ${REFERENCE}""");
    }

    private void testWithExternalApp(String command)
            throws ImporterHandlerException {
        var input = inputAsStream();
        var metadata = new Properties();
        metadata.set(
                "metaFileField1", "this is a first test");
        metadata.set("metaFileField2",
                "this is a second test value1",
                "this is a second test value2");

        var t = new ExternalTagger();
        t.setCommand(ExternalApp.newCommandLine(command));
        addPatternsAndEnvs(t);
        t.setMetadataInputFormat("properties");
        t.setMetadataOutputFormat("properties");
        t.setOnSet(PropertySetter.REPLACE);

        t.tagDocument(TestUtil.newHandlerDoc(
                "N/A", input, metadata), input, ParseState.PRE);

        assertMetadataFiles(metadata);
    }

    private void assertMetadataFiles(Properties meta) {
        Assertions.assertEquals(
                "test first a is this", meta.getString("metaFileField1"));
        Assertions.assertEquals(
                "value1 test second a is this",
                meta.getStrings("metaFileField2").get(0));
        Assertions.assertEquals(
                "value2 test second a is this",
                meta.getStrings("metaFileField2").get(1));
    }

    private void addPatternsAndEnvs(ExternalTagger t) {
        Map<String, String> envs = new HashMap<>();
        envs.put(ExternalApp.ENV_STDOUT_BEFORE, "field1:StdoutBefore");
        envs.put(ExternalApp.ENV_STDOUT_AFTER, "<field2>StdoutAfter</field2>");
        envs.put(ExternalApp.ENV_STDERR_BEFORE, "field3 StdErrBefore");
        envs.put(ExternalApp.ENV_STDERR_AFTER, "StdErrAfter:field4");
        t.setEnvironmentVariables(envs);

        t.setMetadataExtractionPatterns(
            new RegexFieldValueExtractor("^(f.*):(.*)", 1, 2),
            new RegexFieldValueExtractor("^<field2>(.*)</field2>", "field2", 1)
        );
        t.addMetadataExtractionPatterns(
            new RegexFieldValueExtractor("^f.*StdErr.*", "field3", 1),
            new RegexFieldValueExtractor("^(S.*?):(.*)", 2, 1)
        );
    }

    private InputStream inputAsStream() {
        return new ByteArrayInputStream(INPUT.getBytes());
    }
}
