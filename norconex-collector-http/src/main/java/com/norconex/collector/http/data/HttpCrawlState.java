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
package com.norconex.collector.http.data;

import com.norconex.collector.core.data.CrawlState;

/**
 * Represents a URL crawling status.
 * @author Pascal Essiembre
 */
public class HttpCrawlState extends CrawlState { 

    private static final long serialVersionUID = 1466828686562714860L;

    public static final HttpCrawlState TOO_DEEP = 
            new HttpCrawlState("TOO_DEEP");
    public static final HttpCrawlState DELETED = 
            new HttpCrawlState("DELETED");
    public static final HttpCrawlState NOT_FOUND = 
            new HttpCrawlState("NOT_FOUND");
    public static final HttpCrawlState BAD_STATUS = 
            new HttpCrawlState("BAD_STATUS");
    
    protected HttpCrawlState(String state) {
        super(state);
    }
}