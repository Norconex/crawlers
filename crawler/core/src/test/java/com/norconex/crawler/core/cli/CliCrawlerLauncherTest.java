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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterEvent;
import com.norconex.committer.core.service.CommitterServiceEvent;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.junit.ParameterizedGridConnectorTest;
import com.norconex.crawler.core.mocks.cli.MockCliExit;
import com.norconex.crawler.core.mocks.cli.MockCliLauncher;
import com.norconex.crawler.core.mocks.crawler.MockCrawler;
import com.norconex.crawler.core.stubs.StubCrawlerConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CliCrawlerLauncherTest {

    // MAYBE: a class for each crawler action/test?
    // MAYBE: write unit tests for <app> help command
    // MAYBE: test with .variables and .properties and system env/props

    @TempDir
    private Path tempDir;

    @ParameterizedGridConnectorTest
    void testNoArgs(Class<? extends GridConnector> gridConnClass) {
        var exit = launch(gridConnClass);
        assertThat(exit.ok()).isFalse();
        assertThat(exit.getStdErr()).contains("No arguments provided.");
        assertThat(exit.getStdOut()).contains(
                "Usage:",
                "help configcheck");
    }

    @ParameterizedGridConnectorTest
    void testErrors(Class<? extends GridConnector> gridConnClass)
            throws Exception {
        // Bad args
        var exit1 = launch(gridConnClass, "potato", "--soup");
        assertThat(exit1.ok()).isFalse();
        assertThat(exit1.getStdErr()).contains(
                "Unmatched arguments");

        // Non existant config file
        var exit2 = launch(
                gridConnClass,
                "configcheck",
                "-config=" + TimeIdGenerator.next() + "IDontExist");
        assertThat(exit2.ok()).isFalse();
        assertThat(exit2.getStdErr()).contains(
                "Configuration file does not exist");

        // Simulate Picocli Exception
        var exit3 = launch(gridConnClass, "clean", "-config=", "-config=");
        assertThat(exit3.ok()).isFalse();
        assertThat(exit3.getStdErr()).contains(
                "should be specified only once",
                "Usage:",
                "Clean the");

        var exit4 = launch(gridConnClass, "clean", "-config=", "-config=");
        assertThat(exit4.ok()).isFalse();
        assertThat(exit4.getStdErr()).contains(
                "should be specified only once",
                "Usage:",
                "Clean the");

        // Bad config syntax
        var file = tempDir.resolve("badConfig.xml");
        Files.writeString(file, """
                <crawler badAttr="badAttr"></crawler>
                """);
        var exit5 = launch(gridConnClass, "configcheck", "-config=" + file);
        assertThat(exit5.ok()).isFalse();
        assertThat(exit5.getStdErr()).contains(
                "Unrecognized field \"badAttr\"");
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
                "Committers:",
                "Memory (Norconex Committer Core)",
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

        var exit = launch(gridConnClass,
                "configcheck", "-config=" + cfgFile.toAbsolutePath());
        assertThat(exit.ok()).isFalse();
        assertThat(exit.getStdErr()).contains(
                "\"numThreads\" must be greater than or equal to 1.");
    }

    @ParameterizedGridConnectorTest
    void testStoreExportImport(Class<? extends GridConnector> gridConnClass)
            throws IOException {
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

        // Import
        var exit2 = launch(
                gridConnClass,
                "storeimport",
                "-config=" + configFile,
                "-file=" + exportFile);
        assertThat(exit2.ok()).isTrue();
    }

    @ParameterizedGridConnectorTest
    void testStart(Class<? extends GridConnector> gridConnClass)
            throws IOException {
        LOG.debug("=== Run 1: Start ===");
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
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.CRAWLER_CRAWL_END,
                CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_SHUTDOWN_END);

        LOG.debug("=== Run 2: Clean and Start ===");
        var exit2 = launch(
                gridConnClass, "start", "-clean", "-config=");
        assertThat(exit2.ok()).withFailMessage(exit2.getStdErr()).isTrue();
        assertThat(exit2.getEvents()).containsExactly(
                // Clean flow
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
                CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_SHUTDOWN_END,

                // Regular flow
                CrawlerEvent.CRAWLER_CONTEXT_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_CONTEXT_INIT_END,
                CrawlerEvent.CRAWLER_CRAWL_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.CRAWLER_CRAWL_END,
                CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_SHUTDOWN_END);

        //TODO verify that crawlstore from previous run was deleted
        // and recreated
    }

    @ParameterizedGridConnectorTest
    void testClean(Class<? extends GridConnector> gridConnClass)
            throws IOException {
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
                CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_SHUTDOWN_END);
    }

    //TODO move this to its own test with more elaborate tests involving
    // actually stopping a crawl session and being on a cluster?
    @ParameterizedGridConnectorTest
    void testStop(Class<? extends GridConnector> gridConnClass)
            throws IOException {
        var exit = launch(gridConnClass, "stop", "-config=");
        assertThat(exit.ok()).isTrue();
    }

    @ParameterizedGridConnectorTest
    void testConfigRender(Class<? extends GridConnector> gridConnClass)
            throws IOException {
        var cfgFile = StubCrawlerConfig.writeConfigToDir(tempDir, cfg -> {});

        var exit1 = launch(gridConnClass, "configrender", "-config=" + cfgFile);
        assertThat(exit1.ok()).isTrue();
        // check that some entries not explicitely configured are NOT present
        // (with V4, "default" values are not exported):
        assertThat(exit1.getStdOut()).doesNotContain("<importer");

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

    private MockCliExit launch(
            Class<? extends GridConnector> gridConnClass,
            String... cmdArgs) {
        return MockCliLauncher.launch(tempDir, cfg -> cfg
                .setGridConnector(ClassUtil.newInstance(gridConnClass)),
                cmdArgs);
    }
}
