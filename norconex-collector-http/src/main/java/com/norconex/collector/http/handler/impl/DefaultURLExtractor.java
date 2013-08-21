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
package com.norconex.collector.http.handler.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.importer.ContentType;

/**
 * @deprecated use 
 *      {@link com.norconex.collector.http.url.impl.DefaultURLExtractor}
 */
@Deprecated
public class DefaultURLExtractor 
        extends com.norconex.collector.http.url.impl.DefaultURLExtractor {

    private static final long serialVersionUID = 4226040637766284484L;

    private static final Logger LOG = LogManager.getLogger(
            DefaultURLExtractor.class);

    @Override
    public Set<String> extractURLs(Reader document, String documentUrl,
            ContentType contentType) throws IOException {
        LOG.warn("DEPRECATED: use "
                + "com.norconex.collector.http.url.impl."
                + "DefaultURLExtractor "
                + "instead of "
                + "com.norconex.collector.http.handler.impl."
                + "DefaultURLExtractor");
        return super.extractURLs(document, documentUrl, contentType);
    }
}
