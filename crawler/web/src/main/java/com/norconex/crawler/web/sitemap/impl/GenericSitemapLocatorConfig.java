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
package com.norconex.crawler.web.sitemap.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.crawler.web.robot.RobotsTxtProvider;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

/**
 * <p>
 * If there is a sitemap defined as a start reference for the same URL web site,
 * this locator is not used. Otherwise, it tells the crawler to
 * use the sitemap as defined in the web site "robots.txt" file (provided
 * the web site defines one and {@link RobotsTxtProvider} is enabled).
 * If no sitemap resolution was possible from "robots.txt", an attempt will
 * be made to retrieve a sitemap using the configured sitemap paths.
 * Default paths are: <code>/sitemap.xml</code> and
 * <code>/sitemap_index.xml</code>
 * </p>
 *
 * {@nx.xml.usage
 * <sitemapLocator
 *   class="com.norconex.crawler.web.sitemap.impl.GenericSitemapLocator"
 *   robotsTxtSitemapDisabled="[false|true]"
 * >
 *   <paths>
 *     <!--
 *       Disable locating by paths by self-closing this tag.
 *       -->
 *     <path>
 *       (Sitemap URL path relative to web site domain.
 *        Overwriting default when specified.)
 *     </path>
 *   </paths>
 * </sitemapLocator>
 * }
 */
@Data
@Accessors(chain = true)
@FieldNameConstants
public class GenericSitemapLocatorConfig {

    public static final List<String> DEFAULT_PATHS =
            List.of("/sitemap.xml", "/sitemap_index.xml");

    private final List<String> paths = new ArrayList<>(DEFAULT_PATHS);

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
