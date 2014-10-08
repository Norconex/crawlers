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
package com.norconex.collector.http.client;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;

/**
 * Create (and initializes) an {@link HttpClient} to be used for all 
 * HTTP request this crawler will make.  If implementing 
 * {@link CloseableHttpClient} the crawler will take care of closing
 * it properly when crawling ends.
 * 
 * Implementors also implementing IXMLConfigurable must name their XML tag
 * <code>httpClientFactory</code> to ensure it gets loaded properly.
 * @since 1.3.0
 * @author Pascal Essiembre
 */
public interface IHttpClientFactory {

    /**
     * Initializes the HTTP Client used for crawling.
     * @param userAgent the HTTP request "User-Agent" header value
     * @return Apache HTTP Client
     */
	HttpClient createHTTPClient(String userAgent);
}
