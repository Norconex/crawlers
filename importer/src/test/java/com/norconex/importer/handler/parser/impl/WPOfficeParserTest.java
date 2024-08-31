/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.importer.handler.parser.impl;

import static com.norconex.importer.TestUtil.resourceAsFile;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WPOfficeParserTest {

    @TempDir
    static Path folder;

    //--- Quattro Pro ----------------------------------------------------------
    @Test
    void test_WPOffice_QuattroPro_qpw() throws IOException {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/wordperfect/quattropro.qpw"))
                .hasContentType("application/x-quattro-pro; version=9")
                .hasContentFamily("Spreadsheet")
                .hasExtension("qpw")
                .contains("Misc. relative references");
    }

    //--- Word Perfect ---------------------------------------------------------
    @Test
    void test_WPOffice_WordPerfect_wpd() throws IOException {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/wordperfect/wordperfect.wpd"))
                .hasContentType("application/vnd.wordperfect; version=6.x")
                .hasContentFamily("Word Processor")
                .hasExtension("wpd")
                .contains("test test");
    }
}
