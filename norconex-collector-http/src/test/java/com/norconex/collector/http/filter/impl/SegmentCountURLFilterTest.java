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
        Assert.assertFalse("URL wrongfully rejected.", f.acceptURL(url));
        f = new SegmentCountURLFilter(6, OnMatch.EXCLUDE);
        Assert.assertFalse("URL wrongfully accepted.", f.acceptURL(url));
        f = new SegmentCountURLFilter(7, OnMatch.EXCLUDE);
        Assert.assertTrue("URL wrongfully rejected.", f.acceptURL(url));

        //--- Test proper duplicate counts ---
        url = "http://www.example.com/aa/bb/aa/cc/bb/aa/aa/bb/cc/dd.html";
        f = new SegmentCountURLFilter(3, OnMatch.EXCLUDE, true);
        Assert.assertFalse("URL wrongfully rejected.", f.acceptURL(url));
        f = new SegmentCountURLFilter(4, OnMatch.EXCLUDE, true);
        Assert.assertFalse("URL wrongfully accepted.", f.acceptURL(url));
        f = new SegmentCountURLFilter(5, OnMatch.EXCLUDE, true);
        Assert.assertTrue("URL wrongfully rejected.", f.acceptURL(url));

        //--- Test custom separator (query string ---
        url = "http://www.example.com/one/two_three|four-five/page.html";
        f = new SegmentCountURLFilter(5, OnMatch.EXCLUDE);
        f.setSeparator("[/_|-]");
        Assert.assertFalse("URL wrongfully rejected.", f.acceptURL(url));
        f = new SegmentCountURLFilter(6, OnMatch.EXCLUDE);
        f.setSeparator("[/_|-]");
        Assert.assertFalse("URL wrongfully accepted.", f.acceptURL(url));
        f = new SegmentCountURLFilter(7, OnMatch.EXCLUDE);
        f.setSeparator("[/_|-]");
        Assert.assertTrue("URL wrongfully rejected.", f.acceptURL(url));
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
