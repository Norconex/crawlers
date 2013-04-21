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

import java.io.File;
import java.io.Serializable;

public class HttpDocument implements Serializable {

	private static final long serialVersionUID = 4376740210800410675L;
	private final String url;
	private final File localFile;
	private final HttpMetadata metadata;

	public HttpDocument(String url, File localFile) {
		super();
		this.url = url;
		this.localFile = localFile;
		this.metadata = new HttpMetadata(url);
	}

	public String getUrl() {
	    //TODO make it point to meta URL or keep separate to distinguish
	    //between original URL and potentiallly overwritten one?
		return url;
	}

	public File getLocalFile() {
		return localFile;
	}

	public HttpMetadata getMetadata() {
		return metadata;
	}
}
