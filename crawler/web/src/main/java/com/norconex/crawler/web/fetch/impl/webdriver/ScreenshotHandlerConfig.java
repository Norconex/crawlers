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
package com.norconex.crawler.web.fetch.impl.webdriver;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.norconex.crawler.core.doc.CrawlDocMetaConstants;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link ScreenshotHandler}.
 * </p>
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class ScreenshotHandlerConfig extends DocImageHandlerConfig {

    public static final Path DEFAULT_SCREENSHOT_DIR =
            Paths.get("./screenshots");
    public static final String DEFAULT_SCREENSHOT_DIR_FIELD =
            CrawlDocMetaConstants.PREFIX + "screenshot-path";
    public static final String DEFAULT_SCREENSHOT_META_FIELD =
            CrawlDocMetaConstants.PREFIX + "screenshot";

    private String cssSelector;

    public ScreenshotHandlerConfig() {
        setTargetDir(DEFAULT_SCREENSHOT_DIR);
        setTargetDirField(DEFAULT_SCREENSHOT_DIR_FIELD);
        setTargetMetaField(DEFAULT_SCREENSHOT_META_FIELD);
    }
}
