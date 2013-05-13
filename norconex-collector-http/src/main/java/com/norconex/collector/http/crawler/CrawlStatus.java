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
package com.norconex.collector.http.crawler;

import java.io.Serializable;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public enum CrawlStatus implements Serializable { 
    OK, 
    REJECTED, 
    ERROR, 
    UNMODIFIED, 
    TOO_DEEP, 
    DELETED, 
    NOT_FOUND, 
    BAD_STATUS;
    
    private static final int LOGGING_STATUS_PADDING = 10;
    private static final int LOGGING_DEPTH_PADDING = 6;
    
    private final Logger log;
    CrawlStatus() {
        log = LogManager.getLogger(
                this.getClass().getCanonicalName() + "." + toString());
    }
    
    void logInfo(CrawlURL crawlURL){
        if (log.isInfoEnabled()) {
            log.info(StringUtils.leftPad(
                    crawlURL.getStatus().toString(), LOGGING_STATUS_PADDING)
                  + " > " + StringUtils.leftPad(
                    "(" + crawlURL.getDepth() + ") ", LOGGING_DEPTH_PADDING)
                  + crawlURL.getUrl());
        }
    }

}