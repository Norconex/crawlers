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

import static com.norconex.crawler.core.CrawlerTestUtil.cliLaunch;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterEvent;
import com.norconex.committer.core.service.CommitterServiceEvent;
import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.stubs.CrawlerConfigStubs;
import com.norconex.crawler.core.stubs.CrawlerStubs;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CliLauncherTest {

    // Maybe a class for each crawler action/test?

    @TempDir
    private Path tempDir;

//    private Path configFile;
//
//    @BeforeEach
//    void beforeEach() {
//        configFile = CoreStubber.writeSampleConfigToDir(tempDir);
//    }

    @Test
    void testNoArgs() throws IOException {
        var exit = cliLaunch(tempDir);
        assertThat(exit.ok()).isFalse();
        assertThat(exit.getStdErr()).contains("No arguments provided.");
        assertThat(exit.getStdOut()).contains(
            "Usage:",
            "help configcheck"
        );
    }

    @Test
    void testErrors() throws Exception {
        // Bad args
        var exit1 = cliLaunch(tempDir, "potato", "--soup");
        assertThat(exit1.ok()).isFalse();
        assertThat(exit1.getStdErr()).contains(
            "Unmatched arguments"
        );

        // Non existant config file
        var exit2 = cliLaunch(tempDir,
                "configcheck",
                "-config=" + TimeIdGenerator.next() + "IDontExist");
        assertThat(exit2.ok()).isFalse();
        assertThat(exit2.getStdErr()).contains(
            "Configuration file does not exist"
        );

        // Simulate Picocli Exception
        var captured = SystemUtil.callAndCaptureOutput(
                () -> CliCrawlerLauncher.launch(
                        CrawlerStubs.memoryCrawlerBuilder(tempDir),
                        "clean", "-config=", "-config="));
        assertThat(captured.getReturnValue()).isNotZero();
        assertThat(captured.getStdErr()).contains(
                "should be specified only once",
                "Usage:",
                "Clean the");

        // Bad config syntax
        var file = tempDir.resolve("badConfig.xml");
        Files.writeString(file, """
                <crawler badAttr="badAttr"></crawler>
                """);
        var exit3 = cliLaunch(tempDir, "configcheck", "-config=" + file);
        assertThat(exit3.ok()).isFalse();
        assertThat(exit3.getStdErr()).contains(
            "Unrecognized field \"badAttr\""
        );
    }

    @Test
    void testHelp() throws IOException {
        var exit = cliLaunch(tempDir, "-h");
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
            "storeimport"
        );
    }

    @Test
    void testVersion() throws IOException {
        var exit = cliLaunch(tempDir, "-v");
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getStdOut()).contains(
            "C R A W L E R",
            "Committers:",
            "Memory (Norconex Committer Core)",
            "Runtime:",
            "Name:",
            "Version:",
            "Vendor:"
        )
        .doesNotContain("null");
    }

    @Test
    void testConfigCheck() throws IOException {
        var exit = cliLaunch(tempDir, "configcheck", "-config=");
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getStdOut()).containsIgnoringWhitespaces(
                "No configuration errors detected.");
    }

    @Test
    void testStoreExportImport() throws IOException {
        var exportDir = tempDir.resolve("exportdir");
        var exportFile = exportDir.resolve(CrawlerStubs.CRAWLER_ID + ".zip");
        var configFile = CrawlerConfigStubs.writeConfigToDir(
                tempDir, cfg -> {});

        // Export
        var exit1 = cliLaunch(tempDir,
                "storeexport",
                "-config=" + configFile,
                "-dir=" + exportDir);

        assertThat(exit1.ok()).isTrue();
        assertThat(exportFile).isNotEmptyFile();

        // Import
        var exit2 = cliLaunch(tempDir,
                "storeimport",
                "-config=" + configFile,
                "-file=" + exportFile);
        assertThat(exit2.ok()).isTrue();
    }

    @Test
    void testStart() throws IOException {
        LOG.debug("=== Run 1: Start ===");
        var exit1 = cliLaunch(tempDir, "start", "-config=");
        if (!exit1.ok()) {
            LOG.error("Could not start crawler properly. Output:\n{}", exit1);
        }
        assertThat(exit1.ok()).isTrue();
        assertThat(exit1.getEvents()).containsExactly(
                CrawlerEvent.CRAWLER_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_INIT_END,
                CrawlerEvent.CRAWLER_RUN_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.CRAWLER_RUN_END,
                CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_SHUTDOWN_END
        );

        LOG.debug("=== Run 2: Clean and Start ===");
        var exit2 = cliLaunch(
                tempDir, "start", "-clean", "-config=");
        assertThat(exit2.ok()).withFailMessage(exit2.getStdErr()).isTrue();
        assertThat(exit2.getEvents()).containsExactly(
                // Clean flow
                CrawlerEvent.CRAWLER_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_INIT_END,
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
                CrawlerEvent.CRAWLER_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_INIT_END,
                CrawlerEvent.CRAWLER_RUN_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.CRAWLER_RUN_END,
                CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_SHUTDOWN_END
        );


        //TODO verify that crawlstore from previous run was deleted
        // and recreated
    }

    @Test
    void testClean() throws IOException {
        var exit = cliLaunch(tempDir, "clean", "-config=");
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getEvents()).containsExactly(
                CrawlerEvent.CRAWLER_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_INIT_END,
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
                CrawlerEvent.CRAWLER_SHUTDOWN_END
        );
    }

    //TODO move this to its own test with more elaborate tests involving
    // actually stopping a crawl session and being on a cluster?
    @Test
    void testStop() throws IOException {
        var exit = cliLaunch(tempDir, "stop", "-config=");
        assertThat(exit.ok()).isTrue();
    }

    @Test
    void testConfigRender() throws IOException {
        var cfgFile = CrawlerConfigStubs.writeConfigToDir(tempDir, cfg -> {});

        var exit1 = cliLaunch(tempDir, "configrender", "-config=" + cfgFile);
        assertThat(exit1.ok()).isTrue();
        // check that some entries not explicitely configured are NOT present
        // (with V4, "default" values are not exported):
        assertThat(exit1.getStdOut()).doesNotContain("<importer");

        var renderedFile = tempDir.resolve("configrender.xml");
        var exit2 = cliLaunch(tempDir,
                "configrender",
                "-config=" + cfgFile,
                "-output=" + renderedFile);
        assertThat(exit2.ok()).isTrue();
        assertThat(Files.readString(renderedFile).trim()).isEqualTo(
                exit1.getStdOut().trim());
    }

    //TODO write unit tests for <app> help command
    //TODO test with .variables and .properties and system env/props
}
