/* Copyright 2010-2014 Norconex Inc.
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
package com.norconex.collector.http.filter;

import com.norconex.collector.http.doc.HttpMetadata;

/**
 * Filter a document based on their HTTP headers, before the document content
 * is downloaded.
 * <p>
 * It is highly recommended to overwrite the <code>toString()</code> method
 * to representing this filter properly in human-readable form (e.g. logging).
 * It is a good idea to include specifics of this filter so crawler users 
 * can know exactly why documents got accepted/rejected rejected if need be.
 * </p>
 * @author Pascal Essiembre
 */
public interface IHttpHeadersFilter {

    /**
     * Whether to accept a URL HTTP headers.  
     * @param url the URL to accept/reject its headers
     * @param headers HTTP headers associated with the URL
     * @return <code>true</code> if accepted, <code>false</code> otherwise
     */
    boolean acceptDocument(String url, HttpMetadata headers);
    
}
