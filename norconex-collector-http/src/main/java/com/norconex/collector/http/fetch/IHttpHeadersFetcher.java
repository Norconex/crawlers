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
package com.norconex.collector.http.fetch;

import java.io.Serializable;

import org.apache.http.client.HttpClient;

import com.norconex.commons.lang.map.Properties;

/**
 * Fetches the HTTP Header, typically via a HEAD request.
 * @author Pascal Essiembre
 */
public interface IHttpHeadersFetcher extends Serializable {

    /**
     * Returning <code>null</code> means the headers could not be fetched
     * and the associated document will be skipped (treated as rejected).
     * @param httpClient the HTTP Client
     * @param url the url from which to fetch the headers
     * @return  HTTP headers as metadata
     */
    Properties fetchHTTPHeaders(HttpClient httpClient, String url);
	
}
