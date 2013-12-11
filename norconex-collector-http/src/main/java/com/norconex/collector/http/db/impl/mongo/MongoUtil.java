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
package com.norconex.collector.http.db.impl.mongo;

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;

/**
 * Utility method for Mongo operations.
 * @author Pascal Dimassimo
 * @since 1.2
 */
public final class MongoUtil {

    public static final String MONGO_INVALID_DBNAME_CHARACTERS = "/\\.\"*<>:|?$";

    private MongoUtil() {
        super();
    }

    /**
     * Return or generate a DB name
     * 
     * If a valid dbName is provided, it is returned as is. If none is provided,
     * a name is generated from the crawl ID (provided in HttpCrawlerConfig)
     * 
     * @param dbName
     * @param config
     * @throws IllegalArgumentException
     *             if the dbName provided contains invalid characters
     * @return DB name
     */
    public static String getDbNameOrGenerate(String dbName,
            HttpCrawlerConfig config) {

        // If we already have a name, try to use it
        if (dbName != null && dbName.length() > 0) {
            // Validate it
            if (StringUtils
                    .containsAny(dbName, MONGO_INVALID_DBNAME_CHARACTERS)
                    || StringUtils.containsWhitespace(dbName)) {
                throw new IllegalArgumentException("Invalid Mongo DB name: "
                        + dbName);
            }
            return dbName;
        }

        // Generate a name from the crawl ID
        String dbIdName = config.getId();
        // Replace invalid character with '_'
        for (int i = 0; i < MONGO_INVALID_DBNAME_CHARACTERS.length(); i++) {
            char c = MONGO_INVALID_DBNAME_CHARACTERS.charAt(i);
            dbIdName = dbIdName.replace(c, '_');
        }
        // Replace whitespaces
        dbIdName = dbIdName.replaceAll("\\s", "_");
        return dbIdName ;
    }
}
