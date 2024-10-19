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
package com.norconex.crawler.core.client.cli;

import com.norconex.crawler.core.Crawler;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Start a crawler.
 */
@Command(
    name = "start",
    description = "Start a crawler"
)
@EqualsAndHashCode
@ToString
public class CliStartCommand extends CliSubCommandBase {

    @Option(
        names = { "-clean" },
        description = """
                Clean stored data from previous crawl runs \
                before start. Same as invoking the "clean" and \
                "start" commands one after the other.""",
        required = false
    )
    private boolean clean;

    @Override
    protected void runCommand(Crawler crawlerClient) {
        if (clean) {
            crawlerClient.clean();
        }
        crawlerClient.crawl();
    }
}
