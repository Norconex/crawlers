/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.crawler.web.robot.impl;

import com.norconex.crawler.web.robot.RobotsMetaProvider;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>Implementation of {@link RobotsMetaProvider} as per X-Robots-Tag
 * and ROBOTS standards.
 * Extracts robots information from "ROBOTS" meta tag in an HTML page
 * or "X-Robots-Tag" tag in the HTTP header (see
 * <a href="https://developers.google.com/webmasters/control-crawl-index/docs/robots_meta_tag">
 * https://developers.google.com/webmasters/control-crawl-index/docs/robots_meta_tag</a>
 * and
 * <a href="http://www.robotstxt.org/meta.html">
 * http://www.robotstxt.org/meta.html</a>).
 * </p>
 *
 * <p>If you specified a prefix for the HTTP headers, make sure to specify it
 * again here or the robots meta tags will not be found.</p>
 *
 * <p>If robots instructions are provided in both the HTML page and
 * HTTP header, the ones in HTML page will take precedence, and the
 * ones in HTTP header will be ignored.</p>
 *
 * {@nx.xml.usage
 *  <robotsMeta
 *     class="com.norconex.crawler.web.robot.impl.StandardRobotsMetaProvider">
 *     <headersPrefix>(string prefixing headers)</headersPrefix>
 *  </robotsMeta>
 * }
 *
 * {@nx.xml.example
 * <robotsMeta />
 * }
 * <p>
 * The above example ignores robot meta information.
 * </p>
 */
@Data
@Accessors(chain = true)
public class StandardRobotsMetaProviderConfig {

    private String headersPrefix;
}
