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

import java.io.Serializable;

/**
 * Responsible for normalizing URLs.  Normalization is taking a raw URL and
 * modifying it to its most basic or standard form.  In other words, this makes
 * different URLs "equivalent".  This allows to eliminate URL variations
 * that points to the same content (e.g. URL carrying temporary session 
 * information).  This action takes place right after URLs are extracted 
 * from a document, before each of these URLs is even considered
 * for further processing.  Returning null will effectively tells the crawler
 * to not even consider it for processing (it won't go through the regular
 * document processing flow).  You may want to consider {@link IURLFilter} 
 * to exclude URLs as part has the regular document processing flow
 * (may create a trace in the logs and gives you more options).
 * Implementors also implementing IXMLConfigurable must name their XML tag
 * <code>urlNormalizer</code> to ensure it gets loaded properly.
 * @author Pascal Essiembre
 */
public interface IURLNormalizer extends Serializable {

    /**
     * Normalize the given URL.
     * @param url the URL to normalize
     * @return the normalized URL
     */
    String normalizeURL(String url);
    
}
