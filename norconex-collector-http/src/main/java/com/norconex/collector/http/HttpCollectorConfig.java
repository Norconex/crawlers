/* Copyright 2010-2014 Norconex Inc.
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
