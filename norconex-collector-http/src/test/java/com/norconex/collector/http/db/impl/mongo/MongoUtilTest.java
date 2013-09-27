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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;

public class MongoUtilTest {

    @Test
    public void test_getDbNameOrGenerate_generate() throws Exception {

        HttpCrawlerConfig config = new HttpCrawlerConfig();
        String id = "my-crawl";
        config.setId(id);

        assertEquals(id, MongoUtil.getDbNameOrGenerate("", config));
    }

    @Test
    public void test_getDbNameOrGenerate_generate_and_replace()
            throws Exception {

        HttpCrawlerConfig config = new HttpCrawlerConfig();
        String id = "my crawl";
        config.setId(id);

        // Whitespace should be replaced with '_'
        assertEquals("my_crawl", MongoUtil.getDbNameOrGenerate("", config));
    }

    @Test
    public void test_getDbNameOrGenerate_invalid_name() throws Exception {
        HttpCrawlerConfig config = new HttpCrawlerConfig();
        // Tests some of the invalid characters
        checkInvalidName("invalid.name", config);
        checkInvalidName("invalid$name", config);
        checkInvalidName("invalid/name", config);
        checkInvalidName("invalid:name", config);
        checkInvalidName("invalid name", config);
    }

    private void checkInvalidName(String name, HttpCrawlerConfig config) {
        try {
            MongoUtil.getDbNameOrGenerate(name, config);
            fail("Should throw an IllegalArgumentException because the name is invalid");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
}
