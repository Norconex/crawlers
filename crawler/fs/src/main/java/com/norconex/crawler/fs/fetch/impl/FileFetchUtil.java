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
package com.norconex.crawler.fs.fetch.impl;

import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.fs.fetch.FileFetchRequest;

public final class FileFetchUtil {

    private FileFetchUtil() {
    }

    /**
     * Whether a reference starts with any of the given prefixes (typically
     * URI schemes).
     * @param req file fetch request
     * @param prefixes prefixes to compare
     * @return <code>true</code> if the request reference starts with one
     *     of the supplied prefixes
     */
    public static boolean referenceStartsWith(
            FileFetchRequest req, String... prefixes) {

        return Optional.ofNullable(req)
                .map(FileFetchRequest::getDoc)
                .map(CrawlDoc::getReference)
                .map(String::toLowerCase)
                .filter(ref -> StringUtils.startsWithAny(ref, prefixes))
                .isPresent();
    }

    /**
     * <p>
     * Ensures paths to local files can be converted to valid URIs
     * by properly encoding each path segments. Non local files are returned
     * unchanged.
     * </p>
     * <p>
     * We consider a path to be a local file path (absolute or relative)
     * if it matches any of these conditions:
     *     - no scheme
     *     - scheme is "file"
     *     - scheme is one letter (e.g., windows drive letter)
     * </p>
     * @param path the path to encode
     * @return encode encoded path
     */
    public static String uriEncodeLocalPath(String path) {
        if (path == null) {
            return null;
        }
        var scheme = path.replaceFirst("^(.*?):.*", "$1");
        if (scheme == null
                || scheme.length() <= 1 || "file".equalsIgnoreCase(scheme)) {
            // encode segments
            var b = new StringBuilder();
            var m = Pattern.compile("([^\\/:]+|[\\/:]+)").matcher(path);
            while (m.find()) {
                if (StringUtils.containsAny(m.group(), "\\/:")) {
                    b.append(m.group());
                } else {
                    b.append(uriEncodeSegment(m.group()));
                }
            }
            return b.toString();
        }
        return path;
    }

    private static String uriEncodeSegment(String value) {
        // Encode control characters and a handful of specific characters,
        // assuming all others are filename-valid on all major OSes.
        var b = new StringBuilder();
        for (char ch : value.toCharArray()) {
            if (ch >= 0 && ch <= 31 || "<>:;@#=&$,\"/\\|?*".indexOf(ch) > -1) {
                b.append("%" + Integer.toHexString(ch));

            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }
}
