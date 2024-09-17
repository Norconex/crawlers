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
package com.norconex.crawler.web.fetch.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link DocImageHandler}.
 * </p>
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class DocImageHandlerConfig {

    public enum Target {
        /**
         * Store image in metadata field.
         */
        METADATA,
        /**
         * Store image on local directory.
         */
        DIRECTORY
    }

    /**
     * Directory structure when storing images on disk.
     */
    public enum DirStructure {
        /**
         * Create directories for each URL segments, with handling
         * of special characters.
         */
        URL2PATH,
        /**
         * Create directories for each date (e.g., <code>2000/12/31/</code>).
         */
        DATE,
        /**
         * Create directories for each date and time, up to seconds
         * (e.g., <code>2000/12/31/13/34/12/</code>).
         */
        DATETIME
    }

    public static final String DEFAULT_IMAGE_FORMAT = "png";

    protected static final List<Target> DEFAULT_TYPES =
            List.of(Target.DIRECTORY);

    private final List<Target> targets = new ArrayList<>(DEFAULT_TYPES);
    private Path targetDir;
    private String targetDirField;
    private DirStructure targetDirStructure = DirStructure.DATETIME;
    private String targetMetaField;
    private String imageFormat = DEFAULT_IMAGE_FORMAT;

    public List<Target> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    public DocImageHandlerConfig setTargets(List<Target> targets) {
        CollectionUtil.setAll(this.targets, targets);
        return this;
    }
}
