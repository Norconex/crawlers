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
package com.norconex.collector.http;

import java.io.Writer;

import org.apache.commons.configuration.XMLConfiguration;

import com.norconex.collector.core.AbstractCollectorConfig;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;

/**
 * HTTP Collector configuration.
 * @author Pascal Essiembre
 */
public class HttpCollectorConfig extends AbstractCollectorConfig {

	public HttpCollectorConfig() {
        super(HttpCrawlerConfig.class);
    }

    @Override
    protected void saveCollectorConfigToXML(Writer out) {
        // Nothing more than what the super class already saves.
    }

    @Override
    protected void loadCollectorConfigFromXML(XMLConfiguration xml) {
        // Nothing more than what the super class already loads.
    }
}
