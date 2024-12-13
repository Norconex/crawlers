/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.cmd.crawl.operations.sitemap.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * Configuration for {@link GenericSitemapLocator}.
 */
@Data
@Accessors(chain = true)
@FieldNameConstants
public class GenericSitemapLocatorConfig {

    public static final List<String> DEFAULT_PATHS =
            List.of("/sitemap.xml", "/sitemap_index.xml");

    /**
     * The domain-relative URL paths where to look for sitemaps when not
     * supplied as start reference or part of a web site robots.txt file.
     * Defaults to <code>/sitemap.xml</code> and
     * <code>/sitemap_index.xml</code>.
     */
    private final List<String> paths = new ArrayList<>(DEFAULT_PATHS);

    /**
     * Whether to disable checking for the sitemap locations in a web site
     * robots.txt file.
     */
    private boolean robotsTxtSitemapDisabled;

    /**
     * Gets the URL paths, relative to the URL root, from which to try
     * locate and resolve sitemaps. Default paths are
     * "/sitemap.xml" and "/sitemap-index.xml".
     * @return sitemap paths.
     */
    public List<String> getPaths() {
        return Collections.unmodifiableList(paths);
    }

    /**
     * Sets the URL paths, relative to the URL root, from which to try
     * locate and resolve sitemaps.
     * @param paths sitemap paths.
     */
    public void setPaths(List<String> paths) {
        CollectionUtil.setAll(this.paths, paths);
    }
}
