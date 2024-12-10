/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.web.commands.crawl.task.operations.scope.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.web.doc.WebCrawlDocContext;

class GenericUrlScopeResolverTest {

    @Test
    void testUrlCrawlScopeStrategy() {
        var url = "http://example.com/base/for/test.html";
        var diffProtocol = docCtx("https://example.com/diff/protocol.html");
        var diffDomain = docCtx("http://www.example.com/diff/domain.html");
        var diffPort = docCtx("http://example.com:81/diff/port.html");
        var sameSite = docCtx("http://example.com:80/diff/same.html");
        var noSchemeDiffDomain = docCtx("//server1.elsewhere.com");

        var s = new GenericUrlScopeResolver();
        assertThat(s.resolve(url, diffProtocol).isInScope()).isTrue();
        assertThat(s.resolve(url, diffDomain).isInScope()).isTrue();
        assertThat(s.resolve(url, diffPort).isInScope()).isTrue();
        assertThat(s.resolve(url, sameSite).isInScope()).isTrue();
        assertThat(s.resolve(url, noSchemeDiffDomain).isInScope()).isTrue();

        // Protocol
        s = new GenericUrlScopeResolver();
        s.getConfiguration().setStayOnProtocol(true);
        assertThat(s.resolve(url, diffProtocol).isInScope()).isFalse();
        assertThat(s.resolve(url, diffDomain).isInScope()).isTrue();
        assertThat(s.resolve(url, diffPort).isInScope()).isTrue();
        assertThat(s.resolve(url, sameSite).isInScope()).isTrue();
        assertThat(s.resolve(url, noSchemeDiffDomain).isInScope()).isTrue();

        // Domain
        s = new GenericUrlScopeResolver();
        s.getConfiguration().setStayOnDomain(true);
        assertThat(s.resolve(url, diffProtocol).isInScope()).isTrue();
        assertThat(s.resolve(url, diffDomain).isInScope()).isFalse();
        assertThat(s.resolve(url, diffPort).isInScope()).isTrue();
        assertThat(s.resolve(url, sameSite).isInScope()).isTrue();
        assertThat(s.resolve(url, noSchemeDiffDomain).isInScope()).isFalse();

        // Port
        s = new GenericUrlScopeResolver();
        s.getConfiguration().setStayOnPort(true);
        assertThat(s.resolve(url, diffProtocol).isInScope()).isFalse(); // 443
        assertThat(s.resolve(url, diffDomain).isInScope()).isTrue();
        assertThat(s.resolve(url, diffPort).isInScope()).isFalse();
        assertThat(s.resolve(url, sameSite).isInScope()).isTrue();
        assertThat(s.resolve(url, noSchemeDiffDomain).isInScope()).isTrue();

        // Protocol + Domain + Port
        s = new GenericUrlScopeResolver();
        s.getConfiguration()
                .setStayOnProtocol(true)
                .setStayOnDomain(true)
                .setStayOnPort(true);
        assertThat(s.resolve(url, diffProtocol).isInScope()).isFalse();
        assertThat(s.resolve(url, diffDomain).isInScope()).isFalse();
        assertThat(s.resolve(url, diffPort).isInScope()).isFalse();
        assertThat(s.resolve(url, sameSite).isInScope()).isTrue();
        assertThat(s.resolve(url, noSchemeDiffDomain).isInScope()).isFalse();
    }

    @Test
    void testStayOnDomainDepthStrategy() {
        var sub0 = "http://example.com/test0.html";
        var sub1 = "http://sub1.example.com/test1.html";
        var sub2 = "http://sub2.sub1.example.com/test2.html";
        var sub3 = "http://sub3.sub2.sub1.example.com/test3.html";
        var sub4 = "http://different.com/testd.html";

        var s = new GenericUrlScopeResolver();

        // Same or not
        s.getConfiguration().setStayOnDomain(false);
        assertThat(s.resolve(sub0, docCtx(sub1)).isInScope()).isTrue();
        assertThat(s.resolve(sub1, docCtx(sub2)).isInScope()).isTrue();
        assertThat(s.resolve(sub0, docCtx(sub3)).isInScope()).isTrue();
        assertThat(s.resolve(sub1, docCtx(sub3)).isInScope()).isTrue();
        assertThat(s.resolve(sub2, docCtx(sub2)).isInScope()).isTrue();
        assertThat(s.resolve(sub3, docCtx(sub1)).isInScope()).isTrue();
        assertThat(s.resolve(sub1, docCtx(sub4)).isInScope()).isTrue();

        // Same or any sub-domain
        s.getConfiguration().setStayOnDomain(true);
        s.getConfiguration().setIncludeSubdomains(true);
        assertThat(s.resolve(sub0, docCtx(sub0)).isInScope()).isTrue();
        assertThat(s.resolve(sub1, docCtx(sub1)).isInScope()).isTrue();
        assertThat(s.resolve(sub0, docCtx(sub0)).isInScope()).isTrue();
        assertThat(s.resolve(sub1, docCtx(sub1)).isInScope()).isTrue();
        assertThat(s.resolve(sub2, docCtx(sub2)).isInScope()).isTrue();
        assertThat(s.resolve(sub3, docCtx(sub3)).isInScope()).isTrue();
        assertThat(s.resolve(sub1, docCtx(sub1)).isInScope()).isTrue();
    }

    private static WebCrawlDocContext docCtx(String ref) {
        return new WebCrawlDocContext(ref);
    }
}
