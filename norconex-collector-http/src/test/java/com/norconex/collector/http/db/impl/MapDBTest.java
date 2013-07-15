package com.norconex.collector.http.db.impl;
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


import java.io.File;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import com.norconex.collector.http.crawler.CrawlURL;

/**
 * Base class that includes all tests that an implementation of
 * ICrawlURLDatabase should pass.
 */
public class MapDBTest {

    private DB db;
    
    @Before
    public void setUp() throws Exception {
        db = DBMaker.newFileDB(new File("C:\\temp\\dbtest\\mapdb"))
                .closeOnJvmShutdown()
                .cacheDisable()
                .randomAccessFileEnableIfNeeded()
                .writeAheadLogDisable()
                .make();
    }

    @After
    public void tearDown() throws Exception {
        db.commit();
        db.close();
        db = null;
    }
    
	@Test
	public void createDatabaseTest() throws Exception {
        Map<String, CrawlURL> map = 
                db.createHashMap("test").keepCounter(true).make();
        CrawlURL c = new CrawlURL("http://www.example.com", 3);
        c.setDocChecksum("docchecksum1111");
        c.setSitemapPriority(0.8f);
        
        map.put("url1", c);
//        db.commit();
//        db.close();
	}
    @Test
    public void loadDatabaseTest() throws Exception {
        Map<String, CrawlURL> map = db.getHashMap("test");
        System.out.println("SIZE:" + map.size());
        CrawlURL c = map.get("url1");
        Assert.assertTrue(0.8f == c.getSitemapPriority());
    }
}
