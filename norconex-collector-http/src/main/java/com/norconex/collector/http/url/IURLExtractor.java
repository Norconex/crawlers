/* Copyright 2010-2013 Norconex Inc.
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
import java.io.Reader;
import java.io.Serializable;
import java.util.Set;

import com.norconex.commons.lang.file.ContentType;

/**
 * Responsible for extracting URLs out of a document.
 * @author Pascal Essiembre
 */
public interface IURLExtractor extends Serializable  {

	/**
	 * Extracts URLs out of a document.
	 * @param document the document
	 * @param documentUrl document url
	 * @param contentType the document content type
	 * @return a set of URLs
	 * @throws IOException problem reading the document
	 */
    Set<String> extractURLs(
            Reader document, String documentUrl, ContentType contentType)
            throws IOException;
    
}
