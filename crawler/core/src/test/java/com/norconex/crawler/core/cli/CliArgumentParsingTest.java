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

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Fast tests for CLI argument parsing and help text generation.
 * These tests verify the CLI interface without requiring actual
 * cluster coordination or crawling.
 */
class CliArgumentParsingTest {

    @TempDir
    private Path tempDir;

    @Test
    void testNoArgsShowsUsageHelp() {
        var exit = quickLaunch();

        assertThat(exit.getCode()).isNotZero();
        assertThat(exit.getStdErr()).contains("No arguments provided.");
        assertThat(exit.getStdOut()).contains(
                "Usage:",
                "help configcheck");
    }

    @Test
    void testHelpFlagShowsAllCommands() {
        var exit = quickLaunch("-h");

        assertThat(exit.getCode()).isZero();
        assertThat(exit.getStdOut()).contains(
                "Usage:",
                "help",
                "start",
                "stop",
                "configcheck",
                "configrender",
                "clean",
                "storeexport",
                "storeimport");
    }

    @Test
    void testVersionFlagShowsVersionInfo() {
        var exit = quickLaunch("-v");

        assertThat(exit.getCode()).isZero();
        assertThat(exit.getStdOut())
                .contains(
                        "C R A W L E R",
                        "Runtime:",
                        "Name:",
                        "Version:",
                        "Vendor:")
                .doesNotContain("null");
    }

    @Test
    void testInvalidCommandShowsError() {
        var exit = quickLaunch("potato", "--soup");

        assertThat(exit.getCode()).isNotZero();
        assertThat(exit.getStdErr()).contains("Unmatched arguments");
    }

    @Test
    void testDuplicateOptionShowsError() {
        var exit = quickLaunch("start", "-config=");

        assertThat(exit.getCode()).isNotZero();
        assertThat(exit.getStdErr()).contains(
                "should be specified only once",
                "Usage:");
    }

    @Test
    void testNonExistentConfigFileShowsError() {
        var captured = TestCliCrawlerLauncher.capture(
                "configcheck", "-config=/path/that/does/not/exist.xml");

        assertThat(captured.getReturnValue()).isNotZero();
        assertThat(captured.getStdErr()).contains(
                "Configuration file does not exist");
    }

    private TestCliCrawlerExit quickLaunch(String... args) {
        return TestCliCrawlerLauncher
                .builder()
                .args(List.of(args))
                .workDir(tempDir)
                .build()
                .launch();
    }

}
