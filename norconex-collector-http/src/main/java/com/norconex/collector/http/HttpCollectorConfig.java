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
package com.norconex.collector.http;

import java.io.Serializable;

import org.apache.commons.lang3.ArrayUtils;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;

/**
 * HTTP Collector configuration.
 * @author Pascal Essiembre
 */
public class HttpCollectorConfig implements Cloneable, Serializable {

    public static final String DEFAULT_LOGS_DIR = "./logs";
    public static final String DEFAULT_PROGRESS_DIR = "./progress";
    
    private static final long serialVersionUID = -3350877963428801802L;
    private String id;
    private HttpCrawlerConfig[] crawlerConfigs;
    private String progressDir = DEFAULT_PROGRESS_DIR;
    private String logsDir = DEFAULT_LOGS_DIR;
    
    /**
     * Creates a new collector with the given unique id.  It is important
     * the id of the collector is unique amongst your collectors.  This
     * facilitates integration with different systems and facilitates
     * tracking.
     * @param id unique identifier
     */
	public HttpCollectorConfig(String id) {
        super();
        this.id = id;
    }

	public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public HttpCrawlerConfig[] getCrawlerConfigs() {
        return crawlerConfigs;
    }
    public void setCrawlerConfigs(HttpCrawlerConfig[] crawlerConfigs) {
        this.crawlerConfigs = ArrayUtils.clone(crawlerConfigs);
    }

    public String getProgressDir() {
        return progressDir;
    }

    public void setProgressDir(String progressDir) {
        this.progressDir = progressDir;
    }

    public String getLogsDir() {
        return logsDir;
    }

    public void setLogsDir(String logsDir) {
        this.logsDir = logsDir;
    }
}
