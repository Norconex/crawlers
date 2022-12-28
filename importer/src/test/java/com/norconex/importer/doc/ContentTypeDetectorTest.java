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
package com.norconex.importer.doc;

import static com.norconex.commons.lang.file.ContentType.HTML;
import static com.norconex.commons.lang.file.ContentType.PDF;
import static com.norconex.commons.lang.file.ContentType.ZIP;
import static com.norconex.importer.TestUtil.getAliceHtmlFile;
import static com.norconex.importer.TestUtil.getAlicePdfFile;
import static com.norconex.importer.TestUtil.getAliceZipFile;
import static com.norconex.importer.doc.ContentTypeDetector.detect;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

class ContentTypeDetectorTest {

    @Test
    void testDetectFile() throws IOException {
        assertThat(detect(getAliceHtmlFile())).isEqualTo(HTML);
        assertThat(detect(getAlicePdfFile())).isEqualTo(PDF);
        assertThat(detect(getAliceZipFile())).isEqualTo(ZIP);
    }

    @Test
    void testDetectFileString() throws IOException {
        // test proper detection despite wrong extensions:
        assertThat(detect(getAliceHtmlFile(), "alice.docx")).isEqualTo(HTML);
        assertThat(detect(getAlicePdfFile(), "alice.zip")).isEqualTo(PDF);
        assertThat(detect(getAliceZipFile(), "alice.xls")).isEqualTo(ZIP);
        // without name
        assertThat(detect(getAliceZipFile(), " ")).isEqualTo(ZIP);
    }

    @Test
    void testDetectInputStream() throws IOException {
        try (var is = new FileInputStream(getAliceHtmlFile())) {
            assertThat(detect(is)).isEqualTo(HTML);
        }
        try (var is = new FileInputStream(getAlicePdfFile())) {
            assertThat(detect(is)).isEqualTo(PDF);
        }
        try (var is = new FileInputStream(getAliceZipFile())) {
            assertThat(detect(is)).isEqualTo(ZIP);
        }
    }

    @Test
    void testDetectInputStreamString() throws IOException {
        // test proper detection despite wrong extensions:
        try (var is = new FileInputStream(getAliceHtmlFile())) {
            assertThat(detect(is, "alice.docx")).isEqualTo(HTML);
        }
        try (var is = new FileInputStream(getAlicePdfFile())) {
            assertThat(detect(is, "alice.zip")).isEqualTo(PDF);
        }
        try (var is = new FileInputStream(getAliceZipFile())) {
            assertThat(detect(is, "alice.xls")).isEqualTo(ZIP);
        }
    }
}
