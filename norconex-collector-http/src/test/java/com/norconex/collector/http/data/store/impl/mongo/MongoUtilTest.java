/* Copyright 2013-2014 Norconex Inc.
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
