/* Copyright 2022-2023 Norconex Inc.
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
class CliLauncherTest {

    //TODO migrate these:

    /*

    // Maybe a class for each crawler action/test?

    @TempDir
    private Path tempDir;

    private Path configFile;

    @BeforeEach
    void beforeEach() {
        configFile = CoreStubber.writeSampleConfigToDir(tempDir);
    }

    @Test
    void testNoArgs() throws IOException {
        var exit = testLaunch(tempDir);
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
        var exit1 = testLaunch(tempDir, "potato", "--soup");
        assertThat(exit1.ok()).isFalse();
        assertThat(exit1.getStdErr()).contains(
            "Unmatched arguments"
        );

        // Non existant config file
        var exit2 = testLaunch(tempDir,
                "configcheck", "-config=" + configFile + "IDontExist");
        assertThat(exit2.ok()).isFalse();
        assertThat(exit2.getStdErr()).contains(
            "Configuration file does not exist"
        );

        // Simulate Picocli Exception
        var captured = SystemUtil.callAndCaptureOutput(() -> CliLauncher.launch(
                CrawlSession.builder()
                    .crawlSessionConfig(new CrawlSessionConfig())
                    .crawlerFactory((s, c) -> {
                            throw new PicocliException("Fake exception.");
                    }),
                "clean", "-config=" + configFile));
        assertThat(captured.getReturnValue()).isNotZero();
        assertThat(captured.getStdErr()).contains(
                "Fake exception.",
                "Usage:",
                "Clean the");

        // Bad config syntax
        Files.writeString(configFile, """
                <crawlSession id="test-crawlsession">
                  <crawlers badAttr="badAttr"></crawlers>
                </crawlSession>
                """);
        var exit3 = testLaunch(tempDir, "configcheck", "-config=" + configFile);
        assertThat(exit3.ok()).isFalse();
        assertThat(exit3.getStdErr()).contains(
            "2 XML configuration errors detected",
            "Attribute 'badAttr' is not allowed",
            "The content of element 'crawlers' is not complete."
        );
    }

    @Test
    void testHelp() throws IOException {
        var exit = testLaunch(tempDir, "-h");
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
        var exit = testLaunch(tempDir, "-v");
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getStdOut()).contains(
            "Crawler and main components:",
            "Crawler:",
            "Committers:",
            "Runtime:",
            "Name:",
            "Version:",
            "Vendor:"
        )
        .doesNotContain("null");
    }

    @Test
    void testConfigCheck() throws IOException {
        var exit = testLaunch(tempDir, "configcheck", "-config=" + configFile);
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getStdOut()).contains(
                "No XML configuration errors detected.");
    }

    @Test
    void testStoreExportImport() throws IOException {
        var exportDir = tempDir.resolve("exportdir");
        var exportFile = exportDir.resolve("test-crawler.zip");

        // Export
        var exit1 = testLaunch(tempDir,
                "storeexport",
                "-config=" + configFile,
                "-dir=" + exportDir);

        assertThat(exit1.ok()).isTrue();
        assertThat(exportFile).isNotEmptyFile();

        // Import
        var exit2 = testLaunch(tempDir,
                "storeimport",
                "-config=" + configFile,
                "-file=" + exportFile);
        assertThat(exit2.ok()).isTrue();
    }

    @Test
    void testStart() throws IOException {
        LOG.debug("=== Run 1: Start ===");
        var exit1 = testLaunch(tempDir, "start", "-config=" + configFile);
        if (!exit1.ok()) {
            LOG.error("Could not start crawler properly. Output:\n{}", exit1);
        }
        assertThat(exit1.ok()).isTrue();
        assertThat(exit1.getEvents()).containsExactly(
                CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN,
                CrawlerEvent.CRAWLER_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_INIT_END,
                CrawlerEvent.CRAWLER_RUN_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.CRAWLER_RUN_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlSessionEvent.CRAWLSESSION_RUN_END
        );

        //TODO verify that crawlstore was created and at least one
        // doc made the whole journey (also test rejection, deletion, etc).

        LOG.debug("=== Run 2: Clean and Start ===");
        var exit2 = testLaunch(
                tempDir, "start", "-clean", "-config=" + configFile);
        assertThat(exit2.ok()).isTrue();
        assertThat(exit2.getEvents()).containsExactly(
                // Clean flow
                CrawlSessionEvent.CRAWLSESSION_CLEAN_BEGIN,
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
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_CLEAN_END,
                CrawlSessionEvent.CRAWLSESSION_CLEAN_END,

                // Regular flow
                CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN,
                CrawlerEvent.CRAWLER_INIT_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_INIT_END,
                CrawlerEvent.CRAWLER_RUN_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                CrawlerEvent.CRAWLER_RUN_THREAD_END,
                CrawlerEvent.CRAWLER_RUN_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlSessionEvent.CRAWLSESSION_RUN_END
        );


        //TODO verify that crawlstore from previous run was deleted
        // and recreated
    }

    @Test
    void testClean() throws IOException {
        var exit = testLaunch(tempDir, "clean", "-config=" + configFile);
        assertThat(exit.ok()).isTrue();
        assertThat(exit.getEvents()).containsExactly(
                CrawlSessionEvent.CRAWLSESSION_CLEAN_BEGIN,
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
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END,
                CrawlerEvent.CRAWLER_CLEAN_END,
                CrawlSessionEvent.CRAWLSESSION_CLEAN_END
        );
    }

    //TODO move this to its own test with more elaborate tests involving
    // actually stopping a crawl session and being on a cluster?
    @Test
    void testStop() throws IOException {
        var exit = testLaunch(tempDir, "stop", "-config=" + configFile);
        assertThat(exit.ok()).isTrue();
    }

    @Test
    void testConfigRender() throws IOException {
        var exit1 = testLaunch(
                tempDir, "configrender", "-config=" + configFile);
        assertThat(exit1.ok()).isTrue();
        // check that some entries not explicitely configured are present:
        assertThat(exit1.getStdOut()).contains(
            "<defaultParser ",
            ".impl.GenericSpoiledReferenceStrategizer"
        );

        var renderedFile = tempDir.resolve("configrender.xml");
        var exit2 = testLaunch(tempDir,
                "configrender",
                "-config=" + configFile,
                "-output=" + renderedFile);
        assertThat(exit2.ok()).isTrue();
        assertThat(Files.readString(renderedFile).trim()).isEqualTo(
                exit1.getStdOut().trim());
    }
*/

    //TODO write unit tests for <app> help command
    //TODO test with .variables and .properties and system env/props
}
