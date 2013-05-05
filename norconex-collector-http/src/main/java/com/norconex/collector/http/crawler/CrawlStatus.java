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

import com.norconex.collector.http.db.CrawlURL;

public enum CrawlStatus implements Serializable { 
    OK, 
    REJECTED, 
    ERROR, 
    UNMODIFIED, 
    TOO_DEEP, 
    DELETED, 
    NOT_FOUND, 
    BAD_STATUS;
    
    private final Logger LOG;
    CrawlStatus() {
        LOG = LogManager.getLogger(
                this.getClass().getCanonicalName() + "." + toString());
    }
    
    void logInfo(CrawlURL crawlURL){
        if (LOG.isInfoEnabled()) {
            LOG.info(StringUtils.leftPad(
                    crawlURL.getStatus().toString(), 10) + " > " 
                  + StringUtils.leftPad("(" + crawlURL.getDepth() + ") ", 6)
                  + crawlURL.getUrl());
        }
    }

}