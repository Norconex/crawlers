/* Copyright 2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.url;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.file.ContentType;

/**
 * Responsible for finding links in documents.  Links are URLs to be followed
 * with possibly contextual information about that URL (the "a" tag attributes,
 * and text).
 * <p />
 * Implementing classes also implementing {@link IXMLConfigurable} should make
 * sure to name their XML tag "<code>extractor</code>", normally nested
 * in <code>linkExtractors</code> tags.
 * 
 * @author Pascal Essiembre
 */
public interface ILinkExtractor {

    /**
     * Extracts links from a document.
     * @param input the document input stream
     * @param reference document reference (URL)
     * @param contentType the document content type
     * @return a set of links
     * @throws IOException problem reading the document
     */
    Set<Link> extractLinks(
            InputStream input, String reference, ContentType contentType)
            throws IOException;
 
    /**
     * Whether this link extraction should be executed for the given URL
     * and/or content type.
     * @param url the url
     * @param contentType the content type
     * @return <code>true</code> if the given URL is accepted
     */
    boolean accepts(String url, ContentType contentType);
}
