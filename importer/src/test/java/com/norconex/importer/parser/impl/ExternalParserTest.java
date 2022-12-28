/* Copyright 2022 Norconex Inc.
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
package com.norconex.importer.parser.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.text.RegexFieldValueExtractor;
import com.norconex.commons.lang.xml.XML;

class ExternalParserTest {

    @Test
    void testWriteRead() {
        var t = new ExternalParser();
        t.setCommand("my command");
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
            XML.assertWriteRead(t, "parser"));

        assertThat(t.getCommand()).isEqualTo("my command");
        assertThat(t.getMetadataExtractionPatterns()).hasSize(4);
        assertThat(t.getEnvironmentVariables()).hasSize(5);
        assertThat(t.getMetadataOutputFormat()).isEqualTo("xml");
        assertThat(t.getMetadataInputFormat()).isEqualTo("json");
        assertThat(t.getOnSet()).isNull();
        assertThat(t.getTempDir().toString()).contains("some-path");
    }
}
