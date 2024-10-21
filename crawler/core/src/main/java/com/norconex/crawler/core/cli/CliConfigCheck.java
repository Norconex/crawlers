/* Copyright 2019-2024 Norconex Inc.
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

import com.norconex.crawler.core.Crawler;

import picocli.CommandLine.Command;

/**
 * Validate configuration file format and quit.
 */
@Command(
    name = "configcheck",
    description = "Validate configuration file syntax"
)
public class CliConfigCheck extends CliBase {
    @Override
    public void runCommand(Crawler crawlerClient) {
        // Reaching this method means no errors were found. Simply state so.
        out().println();
        out().println("No configuration errors detected.");
    }
}
