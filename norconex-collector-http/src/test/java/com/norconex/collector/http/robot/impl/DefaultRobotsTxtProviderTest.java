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
package com.norconex.collector.http.robot.impl;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Pascal Essiembre
 */
public class DefaultRobotsTxtProviderTest {

    @Test
    public void testGetRobotsTxt() throws IOException {
        String robotTxt1 = 
                "User-agent: *\n"
              + "Disallow: /dontgo/there/\n"
              + "User-agent: mister-crawler\n"
              + "Disallow: /bathroom/\n"
              + "User-agent: miss-crawler\n"
              + "Disallow: /tvremote/\n";
        String robotTxt2 = 
                "User-agent: mister-crawler\n"
              + "Disallow: /bathroom/\n"
              + "User-agent: *\n"
              + "Disallow: /dontgo/there/\n";
        String robotTxt3 = 
                "User-agent: miss-crawler\n"
              + "Disallow: /tvremote/\n"
              + "User-agent: *\n"
              + "Disallow: /dontgo/there/\n";
        String robotTxt4 = 
                "User-agent: miss-crawler\n"
              + "Disallow: /tvremote/\n"
              + "User-agent: *\n"
              + "Disallow: /dontgo/there/\n"
              + "User-agent: mister-crawler\n"
              + "Disallow: /bathroom/\n";
        
        
        Assert.assertEquals("Robots.txt (Disallow:/bathroom/)", 
                parseRobotRule("mister-crawler", robotTxt1));
        Assert.assertEquals("Robots.txt (Disallow:/bathroom/)", 
                parseRobotRule("mister-crawler", robotTxt2));
        Assert.assertEquals("Robots.txt (Disallow:/dontgo/there/)", 
                parseRobotRule("mister-crawler", robotTxt3));
        Assert.assertEquals("Robots.txt (Disallow:/bathroom/)", 
                parseRobotRule("mister-crawler", robotTxt4));
    }
    
    private String parseRobotRule(String agent, String content) 
            throws IOException {
        DefaultRobotsTxtProvider robotProvider = new DefaultRobotsTxtProvider();
        return robotProvider.parseRobotsTxt(
                IOUtils.toInputStream(content), 
                "http://www.testinguseragents.test/some/fake/url.html",
                "mister-crawler").getFilters()[0].toString();
    }
}
