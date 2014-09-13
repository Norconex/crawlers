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
package com.norconex.collector.http.doc;

import java.util.Collection;

import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.ImporterMetadata;

public class HttpMetadata extends ImporterMetadata {

	private static final long serialVersionUID = 1454870639551983430L;

    public static final String COLLECTOR_PREFIX = "collector.http.";
	
	public static final String HTTP_CONTENT_TYPE = "Content-Type";
    public static final String HTTP_CONTENT_LENGTH = "Content-Length";
    
    public static final String COLLECTOR_URL = COLLECTOR_PREFIX + "url";
    public static final String COLLECTOR_CONTENT_TYPE = 
            COLLECTOR_PREFIX + "content-type";
    public static final String COLLECTOR_CONTENT_ENCODING = 
            COLLECTOR_PREFIX + "content-encoding";
    public static final String COLLECTOR_DEPTH = COLLECTOR_PREFIX + "depth";
    public static final String COLLECTOR_SM_LASTMOD = 
            COLLECTOR_PREFIX + "sitemap-lastmod";
    public static final String COLLECTOR_SM_CHANGE_FREQ = 
            COLLECTOR_PREFIX + "sitemap-changefreq";
    public static final String COLLECTOR_SM_PRORITY = 
            COLLECTOR_PREFIX + "sitemap-priority";
    public static final String COLLECTOR_REFERNCED_URLS = 
            COLLECTOR_PREFIX + "referenced-urls";
    public static final String COLLECTOR_CHECKSUM_HEADER = 
            COLLECTOR_PREFIX + "checksum-header";
    public static final String COLLECTOR_CHECKSUM_DOC = 
            COLLECTOR_PREFIX + "checksum-doc";

	public HttpMetadata(String documentURL) {
		super(false);
		setString(COLLECTOR_URL, documentURL);
	}

    public HttpMetadata(Properties metadata) {
        super(metadata, false);
    }
	
	public String getDocumentUrl() {
	    return getString(COLLECTOR_URL);
	}
	public Collection<String> getDocumentUrls() {
	    return getStrings(COLLECTOR_REFERNCED_URLS);
	}
	
}
