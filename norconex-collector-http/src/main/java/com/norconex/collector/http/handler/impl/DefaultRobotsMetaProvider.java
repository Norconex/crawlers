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

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.ContentType;

/**
 * @deprecated use 
 *  {@link com.norconex.collector.http.robot.impl.DefaultRobotsMetaProvider}
 */
@Deprecated
public class DefaultRobotsMetaProvider extends 
        com.norconex.collector.http.robot.impl.DefaultRobotsMetaProvider {

    private static final long serialVersionUID = 5023524792932793111L;

    private static final Logger LOG = LogManager.getLogger(
            DefaultRobotsMetaProvider.class);

    @Override
    public RobotsMeta getRobotsMeta(Reader document, String documentUrl,
            ContentType contentType, Properties httpHeaders) throws IOException {
        LOG.warn("DEPRECATED: use "
                + "com.norconex.collector.http.robot.impl."
                + "DefaultRobotsMetaProvider "
                + "instead of "
                + "com.norconex.collector.http.handler.impl."
                + "DefaultRobotsMetaProvider");
        return super.getRobotsMeta(
                document, documentUrl, contentType, httpHeaders);
    }
}
