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
package com.norconex.collector.http.fetch;

import java.io.Serializable;

import org.apache.http.client.HttpClient;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.doc.HttpDocument;

/**
 * Fetches the HTTP document and its metadata (HTTP Headers).  The 
 * document metadata is populated with the HTTP Headers and the document body
 * is saved to the document local file.
 * @author Pascal Essiembre
 */
public interface IHttpDocumentFetcher extends Serializable {

	/**
	 * Fetches HTTP document and saves it to a local file
	 * @param httpClient the HTTP client
	 * @param doc HttpDocument the document to fetch and save
	 * @return URL status
	 */
	CrawlStatus fetchDocument(
	        HttpClient httpClient, HttpDocument doc);
	
}
