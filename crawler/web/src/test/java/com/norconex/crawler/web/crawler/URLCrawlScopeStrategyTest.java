/* Copyright 2015-2023 Norconex Inc.
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
package com.norconex.crawler.web.crawler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class URLCrawlScopeStrategyTest {

    @Test
    void testURLCrawlScopeStrategy() {
        var url = "http://example.com/base/for/test.html";
        var diffProtocol = "https://example.com/diff/protocol.html";
        var diffDomain = "http://www.example.com/diff/domain.html";
        var diffPort = "http://example.com:81/diff/port.html";
        var sameSite = "http://example.com:80/diff/same.html";
        var noSchemeDiffDomain = "//server1.elsewhere.com";

        var s = new URLCrawlScopeStrategy();

        Assertions.assertTrue(s.isInScope(url, diffProtocol));
        Assertions.assertTrue(s.isInScope(url, diffDomain));
        Assertions.assertTrue(s.isInScope(url, diffPort));
        Assertions.assertTrue(s.isInScope(url, sameSite));
        Assertions.assertTrue(s.isInScope(url, noSchemeDiffDomain));

        // Protocol
        s = new URLCrawlScopeStrategy();
        s.setStayOnProtocol(true);
        Assertions.assertFalse(s.isInScope(url, diffProtocol));
        Assertions.assertTrue(s.isInScope(url, diffDomain));
        Assertions.assertTrue(s.isInScope(url, diffPort));
        Assertions.assertTrue(s.isInScope(url, sameSite));
        Assertions.assertTrue(s.isInScope(url, noSchemeDiffDomain));

        // Domain
        s = new URLCrawlScopeStrategy();
        s.setStayOnDomain(true);
        Assertions.assertTrue(s.isInScope(url, diffProtocol));
        Assertions.assertFalse(s.isInScope(url, diffDomain));
        Assertions.assertTrue(s.isInScope(url, diffPort));
        Assertions.assertTrue(s.isInScope(url, sameSite));
        Assertions.assertFalse(s.isInScope(url, noSchemeDiffDomain));

        // Port
        s = new URLCrawlScopeStrategy();
        s.setStayOnPort(true);
        Assertions.assertFalse(s.isInScope(url, diffProtocol)); // https = 443
        Assertions.assertTrue(s.isInScope(url, diffDomain));
        Assertions.assertFalse(s.isInScope(url, diffPort));
        Assertions.assertTrue(s.isInScope(url, sameSite));
        Assertions.assertTrue(s.isInScope(url, noSchemeDiffDomain));


        // Protocol + Domain + Port
        s = new URLCrawlScopeStrategy();
        s.setStayOnProtocol(true);
        s.setStayOnDomain(true);
        s.setStayOnPort(true);
        Assertions.assertFalse(s.isInScope(url, diffProtocol));
        Assertions.assertFalse(s.isInScope(url, diffDomain));
        Assertions.assertFalse(s.isInScope(url, diffPort));
        Assertions.assertTrue(s.isInScope(url, sameSite));
        Assertions.assertFalse(s.isInScope(url, noSchemeDiffDomain));
    }

    @Test
    void testStayOnDomainDepthStrategy() {
        var sub0 = "http://example.com/test0.html";
        var sub1 = "http://sub1.example.com/test1.html";
        var sub2 = "http://sub2.sub1.example.com/test2.html";
        var sub3 = "http://sub3.sub2.sub1.example.com/test3.html";
        var sub4 = "http://different.com/testd.html";

        var s = new URLCrawlScopeStrategy();

        // Same or not
        s.setStayOnDomain(false);
        Assertions.assertTrue(s.isInScope(sub0, sub1));
        Assertions.assertTrue(s.isInScope(sub1, sub2));
        Assertions.assertTrue(s.isInScope(sub0, sub3));
        Assertions.assertTrue(s.isInScope(sub1, sub3));
        Assertions.assertTrue(s.isInScope(sub2, sub2));
        Assertions.assertTrue(s.isInScope(sub3, sub1));
        Assertions.assertTrue(s.isInScope(sub1, sub4));

        // Same or any sub-domain
        s.setStayOnDomain(true);
        s.setIncludeSubdomains(true);
        Assertions.assertTrue(s.isInScope(sub0, sub1));
        Assertions.assertTrue(s.isInScope(sub1, sub2));
        Assertions.assertTrue(s.isInScope(sub0, sub3));
        Assertions.assertTrue(s.isInScope(sub1, sub3));
        Assertions.assertTrue(s.isInScope(sub2, sub2));
        Assertions.assertFalse(s.isInScope(sub3, sub1));
        Assertions.assertFalse(s.isInScope(sub1, sub4));
    }
}
