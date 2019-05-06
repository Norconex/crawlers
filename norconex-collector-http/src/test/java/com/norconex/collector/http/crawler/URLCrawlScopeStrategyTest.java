/* Copyright 2015-2019 Norconex Inc.
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
package com.norconex.collector.http.crawler;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class URLCrawlScopeStrategyTest {

    @Test
    public void testURLCrawlScopeStrategy() throws IOException {
        String url = "http://example.com/base/for/test.html";
        String diffProtocol = "https://example.com/diff/protocol.html";
        String diffDomain = "http://www.example.com/diff/domain.html";
        String diffPort = "http://example.com:81/diff/port.html";
        String sameSite = "http://example.com:80/diff/same.html";
        String noSchemeDiffDomain = "//server1.elsewhere.com";

        URLCrawlScopeStrategy s = null;

        // No scope defined
        s = new URLCrawlScopeStrategy();
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

}
