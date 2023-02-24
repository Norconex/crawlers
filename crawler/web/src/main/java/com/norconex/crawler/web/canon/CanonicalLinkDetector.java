/* Copyright 2015-2023 Norconex Inc.
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
package com.norconex.crawler.web.canon;

import java.io.IOException;
import java.io.InputStream;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.web.crawler.HttpCrawlerConfig;

/**
 * <p>Detects and return any canonical URL found in documents, whether from
 * the HTTP headers (metadata), or from a page content (usually HTML).
 * Documents having a canonical URL reference in them are rejected in favor
 * of the document represented by the canonical URL.</p>
 *
 * <p>When a {@link HttpCrawlerConfig#getFetchHttpHead()} is enabled,
 * a page won't be downloaded if a canonical link is found in the HTTP headers
 * (saving bandwidth and
 * processing). If not used, or if no canonical link was found, an attempt
 * will be made against the HTTP headers obtained (if any) just after fetching
 * a document. If no canonical link was found there, then the content
 * is evaluated.</p>
 *
 * <p>A canonical link found to be the same as the current page reference is
 * ignored.</p>
 *
 * @since 2.2.0
 */
public interface CanonicalLinkDetector {

    /**
     * Detects from metadata gathered so far, which when invoked, is
     * normally the HTTP header values.
     * @param reference document reference
     * @param metadata metadata object containing HTTP headers
     * @return the detected canonical URL or <code>null</code> if none is found.
     */
    String detectFromMetadata(String reference, Properties metadata);

    /**
     * Detects from a document content the presence of a canonical URL.
     * This occur before a document gets parsed and may apply to only
     * a few content types.
     * @param reference document reference
     * @param is the document content input stream
     * @param contentType the document content type
     * @return the detected canonical URL or <code>null</code> if none is found.
     * @throws IOException problem reading content
     */
    String detectFromContent(
            String reference, InputStream is, ContentType contentType)
                    throws IOException;
}
