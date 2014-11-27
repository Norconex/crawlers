/* Copyright 2010-2014 Norconex Inc.
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

import java.io.IOException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.importer.handler.filter.OnMatch;

public class SegmentCountURLFilterTest {

    SegmentCountURLFilter f;
    String url;

    @After
    public void tearDown() throws Exception {
        f = null;
        url = null;
    }


    @Test
    public void testSegmentMatches() throws IOException {
        //--- Test proper segment counts ---
        url = "http://www.example.com/one/two/three/four/five/page.html";
        f = new SegmentCountURLFilter(5, OnMatch.EXCLUDE);
        Assert.assertFalse("URL wrongfully rejected.", f.acceptReference(url));
        f = new SegmentCountURLFilter(6, OnMatch.EXCLUDE);
        Assert.assertFalse("URL wrongfully accepted.", f.acceptReference(url));
        f = new SegmentCountURLFilter(7, OnMatch.EXCLUDE);
        Assert.assertTrue("URL wrongfully rejected.", f.acceptReference(url));

        //--- Test proper duplicate counts ---
        url = "http://www.example.com/aa/bb/aa/cc/bb/aa/aa/bb/cc/dd.html";
        f = new SegmentCountURLFilter(3, OnMatch.EXCLUDE, true);
        Assert.assertFalse("URL wrongfully rejected.", f.acceptReference(url));
        f = new SegmentCountURLFilter(4, OnMatch.EXCLUDE, true);
        Assert.assertFalse("URL wrongfully accepted.", f.acceptReference(url));
        f = new SegmentCountURLFilter(5, OnMatch.EXCLUDE, true);
        Assert.assertTrue("URL wrongfully rejected.", f.acceptReference(url));

        //--- Test custom separator (query string ---
        url = "http://www.example.com/one/two_three|four-five/page.html";
        f = new SegmentCountURLFilter(5, OnMatch.EXCLUDE);
        f.setSeparator("[/_|-]");
        Assert.assertFalse("URL wrongfully rejected.", f.acceptReference(url));
        f = new SegmentCountURLFilter(6, OnMatch.EXCLUDE);
        f.setSeparator("[/_|-]");
        Assert.assertFalse("URL wrongfully accepted.", f.acceptReference(url));
        f = new SegmentCountURLFilter(7, OnMatch.EXCLUDE);
        f.setSeparator("[/_|-]");
        Assert.assertTrue("URL wrongfully rejected.", f.acceptReference(url));
    }

    @Test
    public void testWriteRead() throws IOException {
        SegmentCountURLFilter f = new SegmentCountURLFilter();
        f.setCount(5);
        f.setDuplicate(true);
        f.setOnMatch(OnMatch.EXCLUDE);
        f.setSeparator("[/&]");
        System.out.println("Writing/Reading this: " + f);
        ConfigurationUtil.assertWriteRead(f);
    }

}
