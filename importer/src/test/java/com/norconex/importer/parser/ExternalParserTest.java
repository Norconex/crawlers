/* Copyright 2017-2023 Norconex Inc.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.RegexFieldValueExtractor;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.parser.impl.ExternalParser;
import com.norconex.importer.util.ExternalApp;

public class ExternalParserTest {

    public static final String INPUT = "1 2 3\n4 5 6\n7 8 9";
    public static final String EXPECTED_OUTPUT = "3 2 1\n6 5 4\n9 8 7";

    @Test
    public void testWriteRead() {
        var p = new ExternalParser();
        p.setCommand("my command");

        p.setMetadataExtractionPatterns(List.of(
            new RegexFieldValueExtractor("asdf.*", "blah"),
            new RegexFieldValueExtractor("qwer.*", "halb")
        ));

        Map<String, String> envs = new HashMap<>();
        envs.put("env1", "value1");
        envs.put("env2", "value2");
        p.setEnvironmentVariables(envs);

        BeanMapper.DEFAULT.assertWriteRead(p);
    }

    @Test
    public void testInFileOutFile() throws DocumentParserException {
        testWithExternalApp("-ic ${INPUT} -oc ${OUTPUT} -ref ${REFERENCE}");
    }
    @Test
    public void testInFileStdout() throws DocumentParserException {
        testWithExternalApp("-ic ${INPUT}");
    }
    @Test
    public void testStdinOutFile() throws DocumentParserException {
        testWithExternalApp("-oc ${OUTPUT} -ref ${REFERENCE}");
    }
    @Test
    public void testStdinStdout() throws DocumentParserException {
        testWithExternalApp("");
    }

    @Test
    public void testMetaInputOutputFiles() throws DocumentParserException {
        testWithExternalApp("""
        	-ic ${INPUT} -oc ${OUTPUT}\s\
        	-im ${INPUT_META} -om ${OUTPUT_META}\s\
        	-ref ${REFERENCE}""", true);
    }

    private void testWithExternalApp(String command)
            throws DocumentParserException {
        testWithExternalApp(command, false);
    }
    private void testWithExternalApp(String command, boolean metaFiles)
            throws DocumentParserException {
        var input = inputAsStream();
        var output = new StringWriter();
        var doc = new Doc(
                "c:\\ref with spaces\\doc.txt",
                new CachedStreamFactory(
                        DataUnit.KB.toBytes(10).intValue(),
                        DataUnit.KB.toBytes(5).intValue())
                                .newInputStream(input));
        var metadata = doc.getMetadata();
        if (metaFiles) {
            metadata.set(
                    "metaFileField1", "this is a first test");
            metadata.set("metaFileField2",
                    "this is a second test value1",
                    "this is a second test value2");
        }

        var p = new ExternalParser();
        p.setCommand(ExternalApp.newCommandLine(command));
        addPatternsAndEnvs(p);
        p.setMetadataInputFormat("properties");
        p.setMetadataOutputFormat("properties");
        p.setOnSet(PropertySetter.REPLACE);
        p.parseDocument(doc, output);

        var content = output.toString();
        // remove any stdout content that could be mixed with output to
        // properly validate
        content = content.replace("field1:StdoutBefore", "");
        content = content.replace("<field2>StdoutAfter</field2>", "");
        content = content.trim();

        Assertions.assertEquals(EXPECTED_OUTPUT, content);
        if (metaFiles) {
            assertMetadataFiles(metadata);
        } else {
            assertMetadata(metadata, command.contains("${REFERENCE}"));
        }
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

    private void assertMetadata(Properties meta, boolean testReference) {
        Assertions.assertEquals("StdoutBefore", meta.getString("field1"));
        Assertions.assertEquals("StdoutAfter", meta.getString("field2"));
        Assertions.assertEquals("field3 StdErrBefore", meta.getString("field3"));
        Assertions.assertEquals("StdErrAfter", meta.getString("field4"));
        if (testReference) {
            Assertions.assertEquals("c:\\ref with spaces\\doc.txt",
                    meta.getString("reference"));
        }
    }

    private void addPatternsAndEnvs(ExternalParser p) {
        Map<String, String> envs = new HashMap<>();
        envs.put(ExternalApp.ENV_STDOUT_BEFORE, "field1:StdoutBefore");
        envs.put(ExternalApp.ENV_STDOUT_AFTER, "<field2>StdoutAfter</field2>");
        envs.put(ExternalApp.ENV_STDERR_BEFORE, "field3 StdErrBefore");
        envs.put(ExternalApp.ENV_STDERR_AFTER, "StdErrAfter:field4");
        p.setEnvironmentVariables(envs);

        p.setMetadataExtractionPatterns(List.of(
            new RegexFieldValueExtractor("^(f.*):(.*)", 1, 2),
            new RegexFieldValueExtractor("^<field2>(.*)</field2>", "field2", 1),
            new RegexFieldValueExtractor("^f.*StdErr.*", "field3", 1),
            new RegexFieldValueExtractor("^(S.*?):(.*)", 2, 1),
            new RegexFieldValueExtractor("^(reference)\\=(.*)", 1, 2)
        ));
    }

    private InputStream inputAsStream() {
        return new ByteArrayInputStream(INPUT.getBytes());
    }
}
