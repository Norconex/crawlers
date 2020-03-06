/* Copyright 2010-2019 Norconex Inc.
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
package com.norconex.collector.http.filter.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.filter.OnMatch;

public class SegmentCountURLFilterTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(SegmentCountURLFilterTest.class);

    SegmentCountURLFilter f;
    String url;

    @AfterEach
    public void tearDown() throws Exception {
        f = null;
        url = null;
    }

    @Test
    public void testSegmentMatches() {
        //--- Test proper segment counts ---
        url = "http://www.example.com/one/two/three/four/five/page.html";
        f = new SegmentCountURLFilter(5, OnMatch.EXCLUDE);
        Assertions.assertFalse(f.acceptReference(url),
                "URL wrongfully rejected.");
        f = new SegmentCountURLFilter(6, OnMatch.EXCLUDE);
        Assertions.assertFalse(f.acceptReference(url),
                "URL wrongfully accepted.");
        f = new SegmentCountURLFilter(7, OnMatch.EXCLUDE);
        Assertions.assertTrue( f.acceptReference(url),
                "URL wrongfully rejected.");

        //--- Test proper duplicate counts ---
        url = "http://www.example.com/aa/bb/aa/cc/bb/aa/aa/bb/cc/dd.html";
        f = new SegmentCountURLFilter(3, OnMatch.EXCLUDE, true);
        Assertions.assertFalse(f.acceptReference(url),
                "URL wrongfully rejected.");
        f = new SegmentCountURLFilter(4, OnMatch.EXCLUDE, true);
        Assertions.assertFalse(f.acceptReference(url),
                "URL wrongfully accepted.");
        f = new SegmentCountURLFilter(5, OnMatch.EXCLUDE, true);
        Assertions.assertTrue(f.acceptReference(url),
                "URL wrongfully rejected.");

        //--- Test custom separator (query string ---
        url = "http://www.example.com/one/two_three|four-five/page.html";
        f = new SegmentCountURLFilter(5, OnMatch.EXCLUDE);
        f.setSeparator("[/_|-]");
        Assertions.assertFalse(f.acceptReference(url),
                "URL wrongfully rejected.");
        f = new SegmentCountURLFilter(6, OnMatch.EXCLUDE);
        f.setSeparator("[/_|-]");
        Assertions.assertFalse(f.acceptReference(url),
                "URL wrongfully accepted.");
        f = new SegmentCountURLFilter(7, OnMatch.EXCLUDE);
        f.setSeparator("[/_|-]");
        Assertions.assertTrue(f.acceptReference(url),
                "URL wrongfully rejected.");
    }

    @Test
    public void testWriteRead() {
        SegmentCountURLFilter f = new SegmentCountURLFilter();
        f.setCount(5);
        f.setDuplicate(true);
        f.setOnMatch(OnMatch.EXCLUDE);
        f.setSeparator("[/&]");
        LOG.debug("Writing/Reading this: {}", f);
        XML.assertWriteRead(f, "filter");
    }

}
