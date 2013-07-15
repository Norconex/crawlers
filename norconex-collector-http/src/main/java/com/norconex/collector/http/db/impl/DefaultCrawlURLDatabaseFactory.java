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
package com.norconex.collector.http.db.impl;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.db.ICrawlURLDatabase;
import com.norconex.collector.http.db.ICrawlURLDatabaseFactory;

/**
 * Database factory creating a {@link MapDBCrawlURLDatabase} instance.
 * @author Pascal Essiembre
 */
public class DefaultCrawlURLDatabaseFactory 
        implements ICrawlURLDatabaseFactory {

    private static final long serialVersionUID = 370632354864351545L;

    @Override
    public ICrawlURLDatabase createCrawlURLDatabase(
            HttpCrawlerConfig config, boolean resume) {
        return new MapDBCrawlURLDatabase(config, resume);
    }

}
