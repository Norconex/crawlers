/* Copyright 2024 Norconex Inc.
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
package com.norconex.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.SystemUtil;

class ImporterLauncherTest {

    private static final String TEST_CONFIG =
            "./src/test/java/com/norconex/importer/test-config.xml";
    private static final String TEST_PDF =
            "./src/test/resources/parser/pdf/plain.pdf";

    @TempDir
    private Path tempDir;

    @Test
    void testLaunch() {
        System.setProperty("tempDir", tempDir.toString());
        assertThatNoException()
                .isThrownBy(() -> ImporterLauncher.launch(new String[] {
                        "--config", TEST_CONFIG,
                        "-i", TEST_PDF,
                        "-o", tempDir + "/output.txt"
                }));
    }

    @Test
    void testLaunchWithVariables() throws IOException {
        System.setProperty("tempDir", tempDir.toString());
        var varFile = tempDir.resolve("variables.properties");
        Files.writeString(varFile, "key=value");
        assertThatNoException()
                .isThrownBy(() -> ImporterLauncher.launch(new String[] {
                        "--config", TEST_CONFIG,
                        "--variables",
                        varFile.toAbsolutePath().toString(),
                        "-i", TEST_PDF,
                        "-o", tempDir + "/output.txt"
                }));
    }

    @Test
    void testInvalidVarFileArg() {
        // use folder as a file to make it fail
        System.setProperty("tempDir", tempDir.toString());
        var varFile = tempDir;
        var exit = SystemUtil.callAndCaptureOutput(
                () -> ImporterLauncher.launch(new String[] {
                        "--config", TEST_CONFIG,
                        "--variables",
                        varFile.toAbsolutePath().toString(),
                        "-i", TEST_PDF,
                        "-o", tempDir + "/output.txt"
                }));
        assertThat(exit.getReturnValue()).isNotZero();
        assertThat(exit.getStdErr()).contains("Invalid variable file path:");
    }

    @Test
    void testInvalidConfigFileArg() {
        // use folder as a file to make it fail
        System.setProperty("tempDir", tempDir.toString());
        var cfgFile = tempDir;
        var exit = SystemUtil.callAndCaptureOutput(
                () -> ImporterLauncher.launch(new String[] {
                        "--config",
                        cfgFile.toAbsolutePath().toString(),
                        "-i", TEST_PDF,
                        "-o", tempDir + "/output.txt"
                }));
        assertThat(exit.getReturnValue()).isNotZero();
        assertThat(exit.getStdErr()).contains(
                "Invalid configuration file path:");
    }

    @Test
    void testCheckConfig() {
        System.setProperty("tempDir", tempDir.toString());
        var exit = SystemUtil.callAndCaptureOutput(
                () -> ImporterLauncher.launch(new String[] {
                        "--config", TEST_CONFIG,
                        "--checkcfg",
                }));
        assertThat(exit.getReturnValue()).isZero();
    }

    @Test
    void testCheckConfigBadConfig() throws IOException {
        System.setProperty("tempDir", tempDir.toString());
        var cfgFile = tempDir.resolve("config.xml");
        Files.writeString(cfgFile, "<*&bad>1 i< 2</*&bad>");
        var exit = SystemUtil.callAndCaptureOutput(
                () -> ImporterLauncher.launch(new String[] {
                        "--config", cfgFile.toString(),
                        "--checkcfg",
                }));
        assertThat(exit.getReturnValue()).isNotZero();
        System.err.println("ERROR: " + exit.getStdErr());
        assertThat(exit.getStdErr()).contains(
                "Could not parse configuration file.");

    }

    @Test
    void testNoOutputFile() throws IOException {
        System.setProperty("tempDir", tempDir.toString());
        var pdfFile = tempDir.resolve("file.pdf");
        Files.copy(Path.of(TEST_PDF), pdfFile);
        var exit = SystemUtil.callAndCaptureOutput(
                () -> ImporterLauncher.launch(new String[] {
                        "--config", TEST_CONFIG,
                        "-i", pdfFile.toString(),
                }));
        assertThat(exit.getReturnValue()).isZero();
    }

    @Test
    void testCliParseError() {
        System.setProperty("tempDir", tempDir.toString());
        var exit = SystemUtil.callAndCaptureOutput(
                () -> ImporterLauncher.launch(new String[] {
                        "--config" // missing file
                }));

        assertThat(exit.getReturnValue()).isNotZero();
        assertThat(exit.getStdErr()).contains(
                "A problem occured while parsing arguments.");
    }

    @Test
    void testBadConfig() throws IOException {
        System.setProperty("tempDir", tempDir.toString());
        var cfgFile = tempDir.resolve("config.xml");
        Files.writeString(cfgFile, "<bad><bad></bad></bad>");
        var exit = SystemUtil.callAndCaptureOutput(
                () -> ImporterLauncher.launch(new String[] {
                        "--config", cfgFile.toString(),
                        "-i", TEST_PDF,
                        "-o", tempDir + "/output.txt"
                }));
        assertThat(exit.getStdErr()).contains(
                "A problem occured loading configuration.");
    }

    @Test
    void testBadConfigSyntax() throws IOException {
        System.setProperty("tempDir", tempDir.toString());
        var cfgFile = tempDir.resolve("config.xml");
        Files.writeString(cfgFile, "<*&bad>1 i< 2</*&bad>");
        var exit = SystemUtil.callAndCaptureOutput(
                () -> ImporterLauncher.launch(new String[] {
                        "--config", cfgFile.toString(),
                        "-i", TEST_PDF,
                        "-o", tempDir + "/output.txt"
                }));
        System.err.println("ERROR: " + exit.getStdErr());
        assertThat(exit.getStdErr()).contains(
                "A problem occured loading configuration.");
    }

    @Test
    void testNoArgs() {
        System.setProperty("tempDir", tempDir.toString());
        var exit = SystemUtil.callAndCaptureOutput(
                () -> ImporterLauncher.launch(new String[] {}));
        assertThat(exit.getReturnValue()).isNotZero();
        assertThat(exit.getStdOut()).contains("importer[.bat|.sh]");
    }
}
