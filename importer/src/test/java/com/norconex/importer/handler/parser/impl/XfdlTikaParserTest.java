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

class XfdlTikaParserTest {

    @TempDir
    static Path folder;

    //--- PureEdge XFDL --------------------------------------------------------
    @Test
    void test_PureEdge_regular_xfdl() throws IOException {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/xfdl/regular.xfdl"))
                .hasContentType("application/vnd.xfdl")
                .hasContentFamily("Other")
                .hasExtension("xfdl")
                .contains("Hey Norconex, this is a test.")
                .contains("Orange")
                .hasMetaValue(
                        "xfdl:formid.title",
                        "Hey Norconex, this is a test.");
    }

    @Test
    void test_PureEdge_base64_xfdl()
            throws IOException {
        ParseAssertions.assertThat(
                resourceAsFile(folder, "/parser/xfdl/base64.xfdl"))
                .hasContentType("application/vnd.xfdl")
                .hasContentFamily("Other")
                .hasExtension("xfdl")
                .contains("Enter order number")
                .contains("check for approval")
                .contains("PART IV - RECOMMENDATIONS")
                .hasMetaValuesCount("xfdl:label.LABEL29.value", 3)
                .hasMetaValue(
                        "xfdl:label.LABEL29.value",
                        "PART IV - RECOMMENDATIONS/APPROVAL/DISAPPROVAL");
    }
}
