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

import java.nio.file.Path;

import com.norconex.crawler.core.Crawler;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Export crawl store to specified file.
 */
@Command(
    name = "storeexport",
    description = "Export crawl store to specified directory"
)
@EqualsAndHashCode
@ToString
public class CliStoreExport extends CliBase {

    @Option(
        names = { "-d", "-dir" },
        description = "Export directory",
        required = true
    )
    private Path dir;
    //TODO add format?
    //TODO add compress?

    @Override
    protected void runCommand(Crawler crawler) {
        crawler.storageExport(dir);
    }
}