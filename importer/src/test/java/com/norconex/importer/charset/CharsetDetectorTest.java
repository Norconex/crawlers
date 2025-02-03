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
package com.norconex.importer.charset;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.Doc;

class CharsetDetectorTest {

    @Test
    void testDetectDoc() {
        assertThatNoException().isThrownBy(() -> {
            var detector = CharsetDetector.builder()
                    .fallbackCharset(StandardCharsets.US_ASCII)
                    .build();

            assertThat(detector.detect(
                    TestUtil.getAliceTextDoc())).isEqualTo(UTF_8);
            assertThat(detector.detect(
                    (Doc) null)).isEqualTo(StandardCharsets.US_ASCII);
        });
    }

    @Test
    void testDetectString() {
        assertThatNoException().isThrownBy(() -> {
            var detector = CharsetDetector.builder()
                    .fallbackCharset(StandardCharsets.US_ASCII)
                    .build();

            assertThat(detector.detect(
                    Files.readString(
                            TestUtil.getAliceTextFile().toPath())))
                                    .isEqualTo(UTF_8);
            assertThat(detector.detect(
                    (String) null)).isEqualTo(StandardCharsets.US_ASCII);
        });
    }

    @Test
    void testDetectInputStream() {
        assertThatNoException().isThrownBy(() -> {
            var detector = CharsetDetector.builder()
                    .fallbackCharset(StandardCharsets.US_ASCII)
                    .build();

            assertThat(detector.detect(
                    TestUtil.getAliceTextDoc().getInputStream()))
                            .isEqualTo(UTF_8);
            assertThat(detector.detect(
                    (InputStream) null)).isEqualTo(StandardCharsets.US_ASCII);
        });
    }

}
