/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.web.event.listeners;

import java.nio.file.Path;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link UrlStatusCrawlerEventListener}.
 * </p>
 * @since 2.2.0
 */
@Data
@Accessors(chain = true)
public class UrlStatusCrawlerEventListenerConfig {

    public static final String DEFAULT_FILENAME_PREFIX = "urlstatuses-";

    /**
     * The coma-separated list of status codes to listen to.
     * Default is {@code null} (listens for all status codes).
     * See class documentation for how to specify code ranges.
     */
    private String statusCodes;

    /**
     * The local directory where this listener report will be written.
     * Default uses the collector working directory.
     */
    private Path outputDir;

    /**
     * The generated report file name prefix. See class documentation
     * for default prefix.
     */
    private String fileNamePrefix = DEFAULT_FILENAME_PREFIX;

    /**
     * Whether to add a timestamp to the file name, to ensure
     * a new one is created with each run.
     */
    private boolean timestamped;
}
