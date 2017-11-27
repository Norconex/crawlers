/* Copyright 2010-2017 Norconex Inc.
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

import java.util.Collection;

import com.norconex.collector.core.doc.CollectorMetadata;
import com.norconex.commons.lang.map.Properties;

public class HttpMetadata extends CollectorMetadata {

	private static final long serialVersionUID = 1454870639551983430L;

	public static final String HTTP_CONTENT_TYPE = "Content-Type";
    public static final String HTTP_CONTENT_LENGTH = "Content-Length";
    
    public static final String COLLECTOR_URL = COLLECTOR_PREFIX + "url";
    public static final String COLLECTOR_DEPTH = COLLECTOR_PREFIX + "depth";
    public static final String COLLECTOR_SM_LASTMOD = 
            COLLECTOR_PREFIX + "sitemap-lastmod";
    public static final String COLLECTOR_SM_CHANGE_FREQ = 
            COLLECTOR_PREFIX + "sitemap-changefreq";
    public static final String COLLECTOR_SM_PRORITY = 
            COLLECTOR_PREFIX + "sitemap-priority";
    public static final String COLLECTOR_REFERENCED_URLS = 
            COLLECTOR_PREFIX + "referenced-urls";
	public static final String COLLECTOR_REFERENCED_URLS_OUT_OF_SCOPE =
            COLLECTOR_PREFIX + "referenced-urls-out-of-scope";
    public static final String COLLECTOR_REFERRER_REFERENCE = 
            COLLECTOR_PREFIX + "referrer-reference";
    public static final String COLLECTOR_REFERRER_LINK_TAG = 
            COLLECTOR_PREFIX + "referrer-link-tag";
    public static final String COLLECTOR_REFERRER_LINK_TEXT = 
            COLLECTOR_PREFIX + "referrer-link-text";
    public static final String COLLECTOR_REFERRER_LINK_TITLE = 
            COLLECTOR_PREFIX + "referrer-link-title";
    /** @since 2.8.0 */
    public static final String COLLECTOR_REDIRECT_TRAIL = 
            COLLECTOR_PREFIX + "redirect-trail";

	public HttpMetadata(String documentURL) {
		super();
		setString(COLLECTOR_URL, documentURL);
	}

    public HttpMetadata(Properties metadata) {
        super(metadata);
    }
	
	public String getDocumentUrl() {
	    return getString(COLLECTOR_URL);
	}
	public Collection<String> getDocumentUrls() {
	    return getStrings(COLLECTOR_REFERENCED_URLS);
	}
	public Collection<String> getDocumentOutOfScopeUrls() {
		return getStrings(COLLECTOR_REFERENCED_URLS_OUT_OF_SCOPE);
	}
	
}
