package com.norconex.collectors.urldatabase.mongo;

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;

public class Utils {

    public static final String MONGO_INVALID_DNBAME_CHARACTERS = "/\\.\"*<>:|?$";

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
                    .containsAny(dbName, MONGO_INVALID_DNBAME_CHARACTERS)
                    || StringUtils.containsWhitespace(dbName)) {
                throw new IllegalArgumentException("Invalid Mongo DB name: "
                        + dbName);
            }
            return dbName;
        }

        // Generate a name from the crawl ID
        dbName = config.getId();
        // Replace invalid character with '_'
        for (int i = 0; i < MONGO_INVALID_DNBAME_CHARACTERS.length(); i++) {
            char c = MONGO_INVALID_DNBAME_CHARACTERS.charAt(i);
            dbName = dbName.replace(c, '_');
        }
        // Replace whitespaces
        dbName = dbName.replaceAll("\\s", "_");
        return dbName;
    }
}
