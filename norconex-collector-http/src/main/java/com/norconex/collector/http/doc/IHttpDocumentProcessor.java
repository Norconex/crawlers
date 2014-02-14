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
package com.norconex.collector.http.doc;

import java.io.Serializable;

import org.apache.http.client.HttpClient;

/**
 * Custom processing (optional) performed on a document.  Can be used 
 * just before of just after a document has been imported.  This is to
 * perform processing on the raw document.  To perform processing on
 * its extracted content, see the Importer for that.
 * @author Pascal Essiembre
 */
public interface IHttpDocumentProcessor extends Serializable {

	/**
	 * Processes a document.
	 * @param httpClient HTTP Client
	 * @param doc the document
	 */
    void processDocument(HttpClient httpClient, HttpDocument doc);
}
