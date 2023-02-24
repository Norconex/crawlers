/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.crawler.web.link;

import java.io.IOException;
import java.util.Set;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.commons.lang.xml.XMLConfigurable;

/**
 * Responsible for finding links in documents.  Links are URLs to be followed
 * with possibly contextual information about that URL (the "a" tag attributes,
 * and text).
 * <br><br>
 * Implementing classes also implementing {@link XMLConfigurable} should make
 * sure to name their XML tag "<code>extractor</code>", normally nested
 * in <code>linkExtractors</code> tags.
 *
 */
public interface LinkExtractor {

    //TODO have ability to return any number of extra info with a link
    // that could be added to target URL as extra metadata.  e.g., store as json.

    Set<Link> extractLinks(CrawlDoc doc) throws IOException;
}
