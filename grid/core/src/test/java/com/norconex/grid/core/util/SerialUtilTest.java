/* Copyright 2025 Norconex Inc.
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
package com.norconex.grid.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SerializationException;
import org.junit.jupiter.api.Test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

class SerialUtilTest {

    private final String sampleJson = """
        {
          "txtField": "someText",
          "intField": 42
        }
        """;
    private final SampleObject sampleObject = new SampleObject("someText", 42);

    @Test
    void testToJsonString() {
        assertThat(SerialUtil.toJsonString(sampleObject))
                .isEqualToIgnoringWhitespace(sampleJson);
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> { //NOSONAR
                    SerialUtil.toJsonString(new Object());
                });
    }

    @Test
    void testToJsonReader() throws IOException {
        assertThat(IOUtils.toString(SerialUtil.toJsonReader(sampleObject)))
                .isEqualToIgnoringWhitespace(sampleJson);
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> { //NOSONAR
                    SerialUtil.toJsonReader(new Object());
                });
    }

    @Test
    void testFromJsonString() {
        assertThat(SerialUtil.fromJson(sampleJson, SampleObject.class))
                .isEqualTo(sampleObject);
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> { //NOSONAR
                    SerialUtil.fromJson("-|-|-", SampleObject.class);
                });
    }

    @Test
    void testFromJsonReader() {
        assertThat(SerialUtil.fromJson(
                new StringReader(sampleJson), SampleObject.class))
                        .isEqualTo(sampleObject);
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> { //NOSONAR
                    SerialUtil.fromJson(new StringReader("-|-|-"),
                            SampleObject.class);
                });
    }

    @Test
    void testToFromJsonParser() {
        var parser = SerialUtil.jsonParser(
                new ByteArrayInputStream(sampleJson.getBytes()));

        assertThat(SerialUtil.fromJson(parser, SampleObject.class))
                .isEqualTo(sampleObject);

        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> { //NOSONAR
                    SerialUtil.fromJson(SerialUtil.jsonParser(
                            new ByteArrayInputStream("_-|_-|".getBytes())),
                            SampleObject.class);
                });
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class SampleObject {
        private String txtField;
        private int intField;
    }
}
