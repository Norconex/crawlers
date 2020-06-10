/* Copyright 2010-2020 Norconex Inc.
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
package com.norconex.collector.http.doc;

import static com.norconex.collector.core.doc.CrawlDocMetadata.PREFIX;

import com.norconex.importer.doc.DocMetadata;

/**
 * Metadata constants for common metadata field
 * names typically set by the HTTP Collector crawler.
 * @author Pascal Essiembre
 * @see DocMetadata
 */
public final class HttpDocMetadata {

    public static final String DEPTH = PREFIX + "depth";
    public static final String SM_LASTMOD = PREFIX + "sitemap-lastmod";
    public static final String SM_CHANGE_FREQ = PREFIX + "sitemap-changefreq";
    public static final String SM_PRORITY = PREFIX + "sitemap-priority";
    public static final String REFERENCED_URLS = PREFIX + "referenced-urls";
	public static final String REFERENCED_URLS_OUT_OF_SCOPE =
            PREFIX + "referenced-urls-out-of-scope";
    public static final String REFERRER_REFERENCE =
            PREFIX + "referrer-reference";
    /** @since 3.0.0 */
    public static final String REFERRER_LINK_PREFIX =
            PREFIX + "referrer-link-";
    /** @since 2.8.0 */
    public static final String REDIRECT_TRAIL = PREFIX + "redirect-trail";
    /** @since 3.0.0 */
    public static final String ORIGINAL_REFERENCE =
            PREFIX + "originalReference";

    private HttpDocMetadata() {
        super();
    }
}
