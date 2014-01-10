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
package com.norconex.collector.http.checksum;

import java.io.Serializable;

import com.norconex.commons.lang.map.Properties;

/**
 * Creates a checksum that uniquely identifies an HTTP document content state 
 * based on its HTTP response header only.  Checksums
 * are used to quickly filter out documents that have already been processed
 * or that have changed since a previous run.
 * <p/>  
 * Two HTTP documents do not have to be <em>equal</em> to return the same 
 * checksum, but they have to be deemed logically the same.  An example of
 * this can be two different URLs pointing to the same document, where only a 
 * single instance should be kept. 
 * <p/>
 * There are no strict rules that define what is equivalent or not.  
 *  
 * @author Pascal Essiembre
 */
public interface IHttpHeadersChecksummer extends Serializable {

    /**
     * Creates a document checksum.
     * @param metadata all HTTP header values
     * @return a checksum value
     */
	String createChecksum(Properties metadata);
	
}
