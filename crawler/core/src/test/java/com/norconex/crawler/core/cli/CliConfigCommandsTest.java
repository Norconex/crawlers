/* Copyright 2022-2025 Norconex Inc.
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
package com.norconex.crawler.core.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.test.standalone.StandaloneCliCrawlerLauncher;

/**
 * Fast tests for config validation and rendering commands.
 * These tests verify configuration parsing and validation logic
 * without requiring cluster coordination.
 */
@WithTestWatcherLogging
@Timeout(30)
class CliConfigCommandsTest {

    @TempDir
    private Path tempDir;

    @Test
    void testConfigCheckValidConfigSucceeds() {
        // Out of the box, the default configuration should (serialize and)
        // validate OK.
        var config = new CrawlConfig();
        config.setStartReferences(List.of("http://example.com"));

        var exit = StandaloneCliCrawlerLauncher
                .builder()
                .args(List.of("configcheck"))
                .workDir(tempDir)
                .build()
                .launch(config);

        assertThat(exit.isOK()).isTrue();
        assertThat(exit.getStdOut()).containsIgnoringWhitespaces(
                "No configuration errors detected.");
    }

    @Test
    void testConfigCheckInvalidSyntaxShowsError() throws IOException {
        var brokenConfigFile = tempDir.resolve("broken.xml");
        Files.writeString(brokenConfigFile,
                "<crawler badAttr=\"badAttr\"></crawler>");
        var captured = StandaloneCliCrawlerLauncher.capture(
                "configcheck", "-config=" + brokenConfigFile);

        assertThat(captured.getReturnValue()).isNotZero();
        assertThat(captured.getStdErr()).contains(
                "Unrecognized field \"badAttr\"");
    }

    @Test
    void testConfigCheckConstraintViolationShowsError() throws IOException {
        var invalidConfigFile = tempDir.resolve("invalid.xml");
        Files.writeString(invalidConfigFile,
                "<crawler numThreads=\"0\"></crawler>");
        var captured = StandaloneCliCrawlerLauncher.capture(
                "configcheck", "-config=" + invalidConfigFile);

        assertThat(captured.getReturnValue()).isNotZero();
        assertThat(captured.getStdErr()).contains("Invalid value");
    }

    @Test
    void testConfigRenderNoDefaultValues() throws IOException {
        var config = new CrawlConfig();
        config.setStartReferences(List.of("http://example.com"));
        var exit = StandaloneCliCrawlerLauncher
                .builder()
                .args(List.of("configrender"))
                .workDir(tempDir)
                .build()
                .launch(config);

        assertThat(exit.isOK()).isTrue();
        // Should NOT contain default values (V4 behavior)
        assertThat(exit.getStdOut()).doesNotContain("<importer");
    }

    @Test
    void testConfigRenderSavesToFile() throws IOException {
        var config = new CrawlConfig();
        config.setStartReferences(List.of("http://example.com"));

        var outputFile = tempDir.resolve("rendered.xml");
        var exit = StandaloneCliCrawlerLauncher
                .builder()
                .args(List.of("configrender",
                        "-output=" + outputFile))
                .workDir(tempDir)
                .build()
                .launch(config);

        assertThat(exit.isOK()).isTrue();
        assertThat(outputFile).exists();

        var renderedContent = Files.readString(outputFile);
        assertThat(renderedContent).contains("startReferences");
    }

    @Test
    void testConfigRenderBadOutputTarget() throws IOException {
        var config = new CrawlConfig();
        config.setStartReferences(List.of("http://example.com"));

        // use a directory instead of file to make it fail
        var badOutputFile = tempDir.toAbsolutePath();
        var exit = StandaloneCliCrawlerLauncher
                .builder()
                .args(List.of("configrender",
                        "-output=" + badOutputFile))
                .workDir(tempDir)
                .build()
                .launch(config);
        assertThat(exit.isOK()).isFalse();
        assertThat(exit.getStdErr()).contains("FileNotFoundException");
    }
}
