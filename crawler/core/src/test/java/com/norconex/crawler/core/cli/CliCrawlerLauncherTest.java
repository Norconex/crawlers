/* Copyright 2022-2024 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterEvent;
import com.norconex.committer.core.service.CommitterServiceEvent;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.grid.GridTestUtil;
import com.norconex.crawler.core.junit.ParameterizedGridConnectorTest;
import com.norconex.crawler.core.mocks.cli.MockCliExit;
import com.norconex.crawler.core.mocks.cli.MockCliLauncher;
import com.norconex.crawler.core.mocks.crawler.MockCrawler;
import com.norconex.crawler.core.stubs.StubCrawlerConfig;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class CliCrawlerLauncherTest {

    // MAYBE: a class for each crawler action/test?
    // MAYBE: write unit tests for <app> help command
    // MAYBE: test with .variables and .properties and system env/props

    @TempDir
    private Path tempDir;

    // Ensures ignite has no hold on the temp files before a cleanup attempt
    // of the tempDir is made by Junit. If we have to do this work around
    // too often, modify the ParameterizedGridConnectorTest to handle this.
    @AfterEach
    void tearDown() {
        GridTestUtil.waitForGridShutdown();
    }

    @ParameterizedGridConnectorTest
    void testNoArgs(Class<? extends GridConnector> gridConnClass) {
        var exit = launch(gridConnClass);
        assertThat(exit.ok()).isFalse();
        assertThat(exit.getStdErr()).contains("No arguments provided.");
        assertThat(exit.getStdOut()).contains(
                "Usage:",
                "help configcheck");
    }

    @Test
    void testErrors()
            throws Exception {
        // Bad args
        var exit1 = launchVerbatim("potato", "--soup");
        assertThat(exit1.ok()).isFalse();
        assertThat(exit1.getStdErr()).contains("Unmatched arguments");

        // Non existant config file
        var exit2 = launchVerbatim(
                "configcheck",
                "-config=" + TimeIdGenerator.next() + "IDontExist");
        assertThat(exit2.ok()).isFalse();
        assertThat(exit2.getStdErr()).contains(
                "Configuration file does not exist");

        // Simulate Picocli Exception
        var exit3 = launchVerbatim("clean", "-config=", "-config=");
        assertThat(exit3.ok()).isFalse();
        assertThat(exit3.getStdErr()).contains(
                "should be specified only once",
                "Usage:",
                "Clean the");

        var exit4 = launchVerbatim("clean", "-config=", "-config=");
        assertThat(exit4.ok()).isFalse();
        assertThat(exit4.getStdErr()).contains(
                "should be specified only once",
                "Usage:",
                "Clean the");

        // Bad config syntax
        var file5 = tempDir.resolve("badConfig.xml");
        Files.writeString(file5, """
                <crawler badAttr="badAttr"></crawler>
                """);
        var exit5 = launchVerbatim("configcheck", "-config=" + file5);
        assertThat(exit5.ok()).isFalse();
        assertThat(exit5.getStdErr()).contains(
                "Unrecognized field \"badAttr\"");

        // Constraint violation
        var file6 = tempDir.resolve("badConfig6.xml");
        Files.writeString(file6, """
                <crawler numThreads="0"></crawler>
                """);
        var exit6 = launchVerbatim("configcheck", "-config=" + file6);
        assertThat(exit6.ok()).isFalse();
        assertThat(exit6.getStdErr()).contains(
                "Invalid value");

    }

    @ParameterizedGridConnectorTest
    void testHelp(Class<? extends GridConnector> gridConnClass) {
        var exit = launch(gridConnClass, "-h");
        assertThat(exit.ok()).isTrue();
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

    @ParameterizedGridConnectorTest
    void testVersion(Class<? extends GridConnector> gridConnClass) {
        var exit = launch(gridConnClass, "-v");
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getStdOut()).contains(
                "C R A W L E R",
                "Runtime:",
                "Name:",
                "Version:",
                "Vendor:")
                .doesNotContain("null");
    }

    @ParameterizedGridConnectorTest
    void testConfigCheck(Class<? extends GridConnector> gridConnClass) {
        var exit = launch(gridConnClass, "configcheck", "-config=");
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getStdOut()).containsIgnoringWhitespaces(
                "No configuration errors detected.");
    }

    @ParameterizedGridConnectorTest
    void testBadConfig(Class<? extends GridConnector> gridConnClass)
            throws IOException {
        var cfgFile = tempDir.resolve("badconfig.xml");
        Files.writeString(cfgFile, """
                <crawler>
                  <numThreads>0</numThreads>
                </crawler>
                """);

        assertThatExceptionOfType(ConstraintViolationException.class)
                .isThrownBy(() -> { //NOSONAR
                    launch(gridConnClass,
                            "configcheck",
                            "-config=" + cfgFile.toAbsolutePath());
                })
                .toString()
                .contains("\"numThreads\" must be greater than or equal to 1.");
    }

    @ParameterizedGridConnectorTest
    void testStoreExportImport(Class<? extends GridConnector> gridConnClass) {
        var exportDir = tempDir.resolve("exportdir");
        var exportFile = exportDir.resolve(MockCrawler.CRAWLER_ID + ".zip");
        var configFile = StubCrawlerConfig.writeConfigToDir(
                tempDir, cfg -> {});

        // Export
        var exit1 = launch(
                gridConnClass,
                "storeexport",
                "-config=" + configFile,
                "-dir=" + exportDir);

        assertThat(exit1.ok()).isTrue();
        assertThat(exportFile).isNotEmptyFile();

        // Wait a bit for mvstore lock to be released.
        Sleeper.sleepSeconds(5);

        // Import
        var exit2 = launch(
                gridConnClass,
                "storeimport",
                "-config=" + configFile,
                "-file=" + exportFile);
        assertThat(exit2.ok()).isTrue();
    }

    @ParameterizedGridConnectorTest
    void testStart(Class<? extends GridConnector> gridConnClass) {
        var exit1 = launch(gridConnClass, "start", "-config=");
        if (!exit1.ok()) {
            LOG.error("Could not start crawler properly. Output:\n{}", exit1);
        }
        assertThat(exit1.ok()).isTrue();
        assertThat(exit1.getEvents()).containsExactly(
                CrawlerEvent.CRAWLER_CONTEXT_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_CONTEXT_INIT_END,
                CrawlerEvent.CRAWLER_CRAWL_BEGIN,
                CrawlerEvent.TASK_RUN_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.TASK_RUN_END,
                CrawlerEvent.CRAWLER_CRAWL_END,
                CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_END);
    }

    @ParameterizedGridConnectorTest
    void testStartAfterClean(Class<? extends GridConnector> gridConnClass) {

        var exit2 = launch(
                gridConnClass, "start", "-clean", "-config=");
        assertThat(exit2.ok()).withFailMessage(exit2.getStdErr()).isTrue();
        assertThat(exit2.getEvents()).containsExactly(
                CrawlerEvent.CRAWLER_CONTEXT_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_CONTEXT_INIT_END,

                // Perform cleaning
                CrawlerEvent.CRAWLER_CLEAN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_END,
                CrawlerEvent.CRAWLER_CLEAN_END,

                // Reset crawl context
                CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_END,
                CrawlerEvent.CRAWLER_CONTEXT_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_CONTEXT_INIT_END,

                // Regular crawl flow
                CrawlerEvent.CRAWLER_CRAWL_BEGIN,
                CrawlerEvent.TASK_RUN_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.TASK_RUN_END,
                CrawlerEvent.CRAWLER_CRAWL_END,
                CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_END);
        //TODO verify that crawlstore from previous run was deleted
        // and recreated
    }

    @ParameterizedGridConnectorTest
    void testClean(Class<? extends GridConnector> gridConnClass) {
        var exit = launch(gridConnClass, "clean", "-config=");
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getEvents()).containsExactly(
                CrawlerEvent.CRAWLER_CONTEXT_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_CONTEXT_INIT_END,
                CrawlerEvent.CRAWLER_CLEAN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_END,
                CrawlerEvent.CRAWLER_CLEAN_END,
                CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_CONTEXT_SHUTDOWN_END);

    }

    //TODO move this to its own test with more elaborate tests involving
    // actually stopping a crawl session and being on a cluster?
    @ParameterizedGridConnectorTest
    void testStop(Class<? extends GridConnector> gridConnClass) {
        // we are just testing that the CLI is launching, not that it actually
        // stopped, which is tested separately.
        var exit = launch(gridConnClass, "stop", "-config=");
        assertThat(exit.ok()).isTrue();
    }

    @ParameterizedGridConnectorTest
    void testConfigRender(Class<? extends GridConnector> gridConnClass)
            throws IOException {
        var cfgFile = StubCrawlerConfig.writeConfigToDir(tempDir, cfg -> {});

        var exit1 = launch(gridConnClass, "configrender", "-config=" + cfgFile);
        assertThat(exit1.ok()).isTrue();
        // check that some entries not explicitly configured are NOT present
        // (with V4, "default" values are not exported):
        assertThat(exit1.getStdOut()).doesNotContain("<importer");

        cfgFile = StubCrawlerConfig.writeConfigToDir(tempDir, cfg -> {});

        var renderedFile = tempDir.resolve("configrender.xml");
        var exit2 = launch(
                gridConnClass,
                "configrender",
                "-config=" + cfgFile,
                "-output=" + renderedFile);

        assertThat(exit2.ok()).isTrue();
        assertThat(Files.readString(renderedFile).trim()).isEqualTo(
                exit1.getStdOut().trim());
    }

    @ParameterizedGridConnectorTest
    void testFailingConfigRender(
            Class<? extends GridConnector> gridConnClass) {
        var exit = launch(
                gridConnClass,
                "configrender",
                "-config=",
                // passing a directory to get FileNotFoundException
                "-output=" + tempDir.toAbsolutePath());
        assertThat(exit.getStdErr()).contains("FileNotFoundException");
    }

    private MockCliExit launchVerbatim(String... cmdArgs) {
        return MockCliLauncher.launchVerbatim(cmdArgs);
    }

    private MockCliExit launch(
            Class<? extends GridConnector> gridConnClass,
            String... cmdArgs) {
        return MockCliLauncher
                .builder()
                .args(List.of(cmdArgs))
                .workDir(tempDir)
                .logErrors(true)
                .configModifier(cfg -> cfg
                        .setGridConnector(ClassUtil.newInstance(gridConnClass)))
                .build()
                .launch();
    }
}
