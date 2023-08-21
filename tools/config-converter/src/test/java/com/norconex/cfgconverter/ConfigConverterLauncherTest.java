/* Copyright 2023 Norconex Inc.
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
package com.norconex.cfgconverter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigConverterLauncherTest {

    @Test
    void testMain(@TempDir Path tempDir) throws IOException {
        var inFile = tempDir.resolve("v3-in.xml");
        var outFile = tempDir.resolve("v4-out.xml");

        Files.writeString(inFile, TestUtil.v3HttpCollectorXmlString());

        ConfigConverterLauncher.launch(
                "-i", inFile.toAbsolutePath().toString(),
                "-o", outFile.toAbsolutePath().toString()
        );


        var expected = TestUtil.v4WebCrawlerXmlString();
        var actual = Files.readString(outFile);
        assertThat(actual).isEqualToIgnoringWhitespace(expected);

        //TODO test converting just importer config
    }
}
