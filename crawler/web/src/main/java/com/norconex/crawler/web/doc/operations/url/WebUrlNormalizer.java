/* Copyright 2010-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.url;

import java.util.List;

import com.norconex.crawler.core.doc.operations.filter.ReferenceFilter;

/**
 * Responsible for normalizing URLs.  Normalization is taking a raw URL and
 * modifying it to its most basic or standard form.  In other words, this makes
 * different URLs "equivalent".  This allows to eliminate URL variations
 * that points to the same content (e.g. URL carrying temporary session
 * information).  This action takes place right after URLs are extracted
 * from a document, before each of these URLs is even considered
 * for further processing.  Returning {@code null} will effectively
 * tell the crawler to not even consider it for processing
 * (it won't go through the regular document processing flow).  You may want
 * to consider {@link ReferenceFilter}
 * to exclude URLs as part has the regular document processing flow
 * (may create a trace in the logs and gives you more options).
 * Implementors also implementing XMLConfigurable must name their XML tag
 * <code>urlNormalizer</code> to ensure it gets loaded properly.
 */
public interface WebUrlNormalizer {

    /**
     * Normalize the given URL.
     * @param url the URL to normalize
     * @return the normalized URL
     */
    String normalizeURL(String url);

    /**
     * Normalizes a URL by applying each normalizers in the list.
     * @param url the URL to normalize
     * @param normalizers the normalizers
     * @return the normalized URL
     */
    static String normalizeURL(String url, List<WebUrlNormalizer> normalizers) {
        if (normalizers == null) {
            return url;
        }
        var normalizedUrl = url;
        for (var norm : normalizers) {
            if (norm != null) {
                normalizedUrl = norm.normalizeURL(normalizedUrl);
            }
        }
        return normalizedUrl;
    }

}
