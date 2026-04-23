/* Copyright 2026 Norconex Inc.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.Crawler;

import picocli.CommandLine;

@Timeout(30)
class CliStopTest {

    @Test
    void parseArgs_noUrlProvided_urlsListIsEmpty() throws Exception {
        var cliStop = parseStop("stop", "-config=dummy.xml");

        assertThat(urlsOf(cliStop)).isEmpty();
    }

    @Test
    void parseArgs_collectsMultipleUrls() throws Exception {
        var cliStop = parseStop(
                "stop",
                "-config=dummy.xml",
                "-url=http://node1:8080",
                "-url=http://node2:8080");

        assertThat(urlsOf(cliStop))
                .containsExactly("http://node1:8080", "http://node2:8080");
    }

    @Test
    void runCommand_forwardsUrlsToCrawlerStop() throws Exception {
        var crawler = mock(Crawler.class);
        var cliStop = new CliStop();
        setUrls(cliStop, List.of("http://node1:8080", "http://node2:8080"));

        cliStop.runCommand(crawler);

        verify(crawler).stop("http://node1:8080", "http://node2:8080");
    }

    @Test
    void runCommand_withNoUrlsForwardsEmptyVarargs() throws Exception {
        var crawler = mock(Crawler.class);
        var cliStop = new CliStop();
        setUrls(cliStop, List.of());

        cliStop.runCommand(crawler);

        verify(crawler).stop();
    }

    @Test
    void equalsAndToString_reflectConfiguredUrls() throws Exception {
        var first = new CliStop();
        var second = new CliStop();
        setUrls(first, List.of("http://node1:8080"));
        setUrls(second, List.of("http://node1:8080"));

        assertThat(first).isEqualTo(second);
        assertThat(first.toString()).contains("http://node1:8080");
    }

    private CliStop parseStop(String... args) {
        var result = new CommandLine(new CliRunner(mock(CrawlDriver.class)))
                .parseArgs(args);
        return (CliStop) result.subcommand().commandSpec().userObject();
    }

    @SuppressWarnings("unchecked")
    private List<String> urlsOf(CliStop cliStop) throws Exception {
        var field = CliStop.class.getDeclaredField("urls");
        field.setAccessible(true);
        return (List<String>) field.get(cliStop);
    }

    private void setUrls(CliStop cliStop, List<String> urls) throws Exception {
        Field field = CliStop.class.getDeclaredField("urls");
        field.setAccessible(true);
        field.set(cliStop, urls);
    }
}
