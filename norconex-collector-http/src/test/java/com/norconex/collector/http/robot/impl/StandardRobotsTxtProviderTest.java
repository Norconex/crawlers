/* Copyright 2010-2015 Norconex Inc.
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
package com.norconex.collector.http.robot.impl;

import com.norconex.collector.core.filter.IReferenceFilter;

import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Pascal Essiembre
 */
public class StandardRobotsTxtProviderTest {

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
                " User-agent : mister-crawler \n"
              + "  Disallow : /bathroom/ \n"
              + "   User-agent : * \n"
              + "    Disallow : /dontgo/there/ \n";
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
        String robotTxt5 = 
                "# robots.txt\n"
              + "User-agent: *\n"
              + "Disallow: /some/fake/ # Spiders, keep out! \n"
              + "Disallow: /spidertrap/\n"
              + "Allow: /open/\n"
              + " Allow : / \n";
        // An empty Disallow means allow all.
        // Test made for https://github.com/Norconex/collector-http/issues/129
        // Standard: https://en.wikipedia.org/wiki/Robots_exclusion_standard
        String robotTxt6 = 
                "User-agent: *\n\n"
              + "Disallow: \n\n";
        
        
        Assert.assertEquals("Robots.txt (Disallow:/bathroom/)",
                parseRobotRule("mister-crawler", robotTxt1)[1].toString());
        Assert.assertEquals("Robots.txt (Disallow:/bathroom/)",
                parseRobotRule("mister-crawler", robotTxt2)[0].toString());
        Assert.assertEquals("Robots.txt (Disallow:/dontgo/there/)",
                parseRobotRule("mister-crawler", robotTxt3)[0].toString());
        Assert.assertEquals("Robots.txt (Disallow:/bathroom/)",
                parseRobotRule("mister-crawler", robotTxt4)[1].toString());
        
        Assert.assertEquals("Robots.txt (Disallow:/some/fake/)",
                parseRobotRule("mister-crawler", robotTxt5)[0].toString());
        Assert.assertEquals("Robots.txt (Disallow:/spidertrap/)",
                parseRobotRule("mister-crawler", robotTxt5)[1].toString());
        Assert.assertEquals("Robots.txt (Allow:/open/)", 
                parseRobotRule("mister-crawler", robotTxt5)[2].toString());
        Assert.assertEquals(3, 
                parseRobotRule("mister-crawler", robotTxt5).length);

        Assert.assertTrue(ArrayUtils.isEmpty(
                parseRobotRule("mister-crawler", robotTxt6)));

    }
    
    private IReferenceFilter[] parseRobotRule(String agent, String content, String url) 
            throws IOException {
        StandardRobotsTxtProvider robotProvider = new StandardRobotsTxtProvider();
        return robotProvider.parseRobotsTxt(
                IOUtils.toInputStream(content), 
                url,
                "mister-crawler").getFilters();
    }
    
    private IReferenceFilter[] parseRobotRule(String agent, String content) 
            throws IOException {
        return parseRobotRule(agent, content, 
                "http://www.testinguseragents.test/some/fake/url.html");
    }
}
