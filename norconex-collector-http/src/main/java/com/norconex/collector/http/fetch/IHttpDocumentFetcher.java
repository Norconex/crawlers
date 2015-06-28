/* Copyright 2010-2015 Norconex Inc.
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
package com.norconex.collector.http.fetch;

import org.apache.http.client.HttpClient;

import com.norconex.collector.http.doc.HttpDocument;

/**
 * Fetches the HTTP document and its metadata (HTTP Headers).  The 
 * document metadata is populated with the HTTP Headers and the document body
 * is saved to the document local file.
 * @author Pascal Essiembre
 */
public interface IHttpDocumentFetcher {

	/**
	 * Fetches HTTP document and saves it to a local file
	 * @param httpClient the HTTP client
	 * @param doc NewHttpDocument the document to fetch and save
	 * @return URL status
	 */
    HttpFetchResponse fetchDocument(
	        HttpClient httpClient, HttpDocument doc);
	
}
