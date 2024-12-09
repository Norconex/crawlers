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
package com.norconex.crawler.web.commands.crawl.task.operations.link.impl;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

final class LinkUtil {

    private LinkUtil() {
    }

    /**
     * Extracts the URL portion of an http-equiv refresh content attribute.
     * E.g.: &lt;meta http-equiv="refresh" content="...">
     * @param contentAttrib content attribute value
     * @return the extracted URL, or <code>null</code>
     */
    static String extractHttpEquivRefreshContentUrl(String contentAttrib) {
        return Optional.ofNullable(contentAttrib)
                .map(attr -> attr.trim().replaceFirst("^\\d+;", ""))
                .map(url -> url.trim().replaceFirst("(?i)^url\\s*=(.*)$", "$1"))
                .map(url -> StringUtils.strip(url, "\"'"))
                .orElse(null);
    }
}
