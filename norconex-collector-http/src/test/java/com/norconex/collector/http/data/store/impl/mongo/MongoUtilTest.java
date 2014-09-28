/* Copyright 2013-2014 Norconex Inc.
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
package com.norconex.collector.http.data.store.impl.mongo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.norconex.collector.core.data.store.impl.mongo.MongoUtil;

public class MongoUtilTest {

    @Test
    public void testGetDbNameOrGenerateDoGenarate() throws Exception {
        String id = "my-crawl";
        assertEquals(id, MongoUtil.getDbNameOrGenerate("", id));
    }

    @Test
    public void testGetDbNameOrGenerateDoGenerateAndReplace()
            throws Exception {
        String id = "my crawl";
        // Whitespace should be replaced with '_'
        assertEquals("my_crawl", MongoUtil.getDbNameOrGenerate("", id));
    }

    @Test
    public void testGetDbNameOrGenerateInvalidName() throws Exception {
        // Tests some of the invalid characters
        checkInvalidName("invalid.name");
        checkInvalidName("invalid$name");
        checkInvalidName("invalid/name");
        checkInvalidName("invalid:name");
        checkInvalidName("invalid name");
    }

    private void checkInvalidName(String name) {
        try {
            MongoUtil.getDbNameOrGenerate(name, null);
            fail("Should throw an IllegalArgumentException "
                    + "because the name is invalid");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
}
