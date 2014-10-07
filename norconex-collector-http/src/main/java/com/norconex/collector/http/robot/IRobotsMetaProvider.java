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
package com.norconex.collector.http.robot;

import java.io.IOException;
import java.io.Reader;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;

/**
 * Responsible for extracting robot information from a page.
 * @author Pascal Essiembre
 */
public interface IRobotsMetaProvider {

    /**
     * Extracts Robots meta information for a page, if any.
     * @param document the document
     * @param documentUrl document url
     * @param contentType the document content type
     * @param httpHeaders the document HTTP Headers
     * @return robots meta instance
     * @throws IOException problem reading the document
     */
    RobotsMeta getRobotsMeta(
            Reader document, String documentUrl, ContentType contentType,
            Properties httpHeaders) throws IOException;
}
