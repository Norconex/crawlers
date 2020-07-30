/* Copyright 2014-2020 Norconex Inc.
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
package com.norconex.collector.http.link;

import java.io.IOException;
import java.util.Set;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.importer.parser.ParseState;

/**
 * Responsible for finding links in documents.  Links are URLs to be followed
 * with possibly contextual information about that URL (the "a" tag attributes,
 * and text).
 * <br><br>
 * Implementing classes also implementing {@link IXMLConfigurable} should make
 * sure to name their XML tag "<code>extractor</code>", normally nested
 * in <code>linkExtractors</code> tags.
 *
 * @author Pascal Essiembre
 */
public interface ILinkExtractor {

    //TODO have ability to return any number of extra info with a link
    // that could be added to target URL as extra metadata.  e.g., store as json.

    //TODO use restrictTo in abstract class instead of accept().

    Set<Link> extractLinks(CrawlDoc doc, ParseState parseState)
            throws IOException;


//    /**
//     * Extracts links from a document.
//     * @param input the document input stream
//     * @param reference document reference (URL)
//     * @param contentType the document content type
//     * @return a set of links
//     * @throws IOException problem reading the document
//     * @deprecated Since 3.0.0, use {@link #extractLinks(CrawlDoc, ParseState)}
//     */
//    @Deprecated
//    default Set<Link> extractLinks(
//            InputStream input, String reference, ContentType contentType)
//            throws IOException { return new HashSet<>(0); };
//
//    /**
//     * Whether this link extraction should be executed for the given URL
//     * and/or content type.
//     * @param url the url
//     * @param contentType the content type
//     * @return <code>true</code> if the given URL is accepted
//     * @deprecated Since 3.0.0, use {@link #extractLinks(CrawlDoc, ParseState)}
//     */
//    @Deprecated
//    default boolean accepts(String url, ContentType contentType)
//            { return true; };
}
