/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.robot.impl;

import static com.norconex.crawler.web.mocks.MockWebsite.serverUrl;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.MediaType;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.operations.filter.ReferenceFilter;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.web.doc.operations.robot.RobotsTxtFilter;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.junit.WebCrawlTest;

@MockServerSettings
class StandardRobotsTxtProviderTest {

    @WebCrawlTest(focus = Focus.CONTEXT)
    void testGetRobotsTxt(ClientAndServer client, CrawlerContext ctx) {

        client.when(request().withPath("/robots.txt"))
                .respond(response()
                        .withStatusCode(302)
                        .withHeader("Location", serverUrl(
                                client, "redirected-robots.txt")));

        client.when(request().withPath("/redirected-robots.txt"))
                .respond(response()
                        .withBody("""
                                User-agent: *
                                Disallow: /badpath/
                                """, MediaType.HTML_UTF_8));

        var robotProvider = new StandardRobotsTxtProvider();
        var robotsTxt = robotProvider.getRobotsTxt(
                (HttpFetcher) ctx.getFetcher(),
                serverUrl(client, "/index.html"));

        assertThat(robotsTxt.getAllowFilters()).isEmpty();
        assertThat(robotsTxt.getDisallowFilters()).hasSize(1);
        assertThat(robotsTxt.getDisallowFilters().get(0))
                .matches(r -> r.acceptReference(
                        serverUrl(client, "/goodpath/a.html")))
                .matches(r -> !r.acceptReference(
                        serverUrl(client, "/badpath/a.html")));
    }

    @Test
    void testParseRobotsTxt() throws IOException {
        var robotTxt1 = """
                User-agent: *
                Disallow: /dontgo/there/
                User-agent: mister-crawler
                Disallow: /bathroom/
                User-agent: miss-crawler
                Disallow: /tvremote/
                """;
        var robotTxt2 =
                """
                         User-agent : mister-crawler\s
                          Disallow : /bathroom/\s
                           User-agent : *\s
                            Disallow : /dontgo/there/\s
                        """;
        var robotTxt3 = """
                User-agent: miss-crawler
                Disallow: /tvremote/
                User-agent: *
                Disallow: /dontgo/there/
                """;
        var robotTxt4 = """
                User-agent: miss-crawler
                Disallow: /tvremote/
                User-agent: *
                Disallow: /dontgo/there/
                User-agent: mister-crawler
                Disallow: /bathroom/
                """;
        var robotTxt5 = """
                # robots.txt
                User-agent: *
                Disallow: /some/fake/ # Spiders, keep out!\s
                Disallow: /spidertrap/
                Allow: /open/
                 Allow : /\s
                """;
        // An empty Disallow means allow all.
        // Test made for https://github.com/Norconex/collector-http/issues/129
        // Standard: https://en.wikipedia.org/wiki/Robots_exclusion_standard
        var robotTxt6 = """
                User-agent: *
                Disallow:
                """;

        // Make sure trailing comments do not throw it off.
        var robotTxt7 = """
                User-agent: *
                Disallow: # allow all
                """;

        assertStartsWith(
                "Robots.txt -> Disallow: /bathroom/",
                parseRobotRule("mister-crawler", robotTxt1).get(0));
        assertStartsWith(
                "Robots.txt -> Disallow: /bathroom/",
                parseRobotRule("mister-crawler", robotTxt2).get(0));
        assertStartsWith(
                "Robots.txt -> Disallow: /dontgo/there/",
                parseRobotRule("mister-crawler", robotTxt3).get(0));
        assertStartsWith(
                "Robots.txt -> Disallow: /bathroom/",
                parseRobotRule("mister-crawler", robotTxt4).get(0));

        assertStartsWith(
                "Robots.txt -> Disallow: /some/fake/",
                parseRobotRule("mister-crawler", robotTxt5).get(0));
        assertStartsWith(
                "Robots.txt -> Disallow: /spidertrap/",
                parseRobotRule("mister-crawler", robotTxt5).get(1));
        assertStartsWith(
                "Robots.txt -> Allow: /open/",
                parseRobotRule("mister-crawler", robotTxt5).get(2));
        Assertions.assertEquals(
                3,
                parseRobotRule("mister-crawler", robotTxt5).size());

        Assertions.assertTrue(
                parseRobotRule("mister-crawler", robotTxt6).isEmpty());
        Assertions.assertTrue(
                parseRobotRule("mister-crawler", robotTxt7).isEmpty());
    }

    @Test
    void testWildcardPattern() throws IOException {
        var robotTxt = "User-agent: *\n\nDisallow: /testing/*/wildcards\n";
        ReferenceFilter rule =
                parseRobotRule("mister-crawler", robotTxt).get(0);

        assertMatch(
                "http://www.test.com/testing/some/random/path/wildcards", rule);
        assertMatch(
                "http://www.test.com/testing/some/random/path/wildcards/test",
                rule);

        assertNoMatch("http://www.test.com/testing/wildcards", rule);
        assertNoMatch("http://www.test.com/wildcards", rule);
    }

    @Test
    void testStringEndPattern() throws IOException {
        var robotTxt = "User-agent: *\n\nDisallow: /testing/anchors$\n";
        ReferenceFilter rule =
                parseRobotRule("mister-crawler", robotTxt).get(0);

        assertMatch("http://www.test.com/testing/anchors", rule);
        assertMatch("http://www.test.com/testing/anchors/", rule);

        assertNoMatch("http://www.test.com/testing/anchors/test", rule);
        assertNoMatch("http://www.test.com/randomly/testing/anchors", rule);
    }

    @Test
    void testRegexEscape() throws IOException {
        var robotTxt = "User-agent: *\n\nDisallow: /testing/reg.ex/escape?\n";
        ReferenceFilter rule =
                parseRobotRule("mister-crawler", robotTxt).get(0);

        assertMatch("http://www.test.com/testing/reg.ex/escape?", rule);
        assertMatch("http://www.test.com/testing/reg.ex/escape?test", rule);

        assertNoMatch("http://www.test.com/testing/reggex/escape?", rule);
        assertNoMatch("http://www.test.com/testing/reggex/escape?test", rule);
        assertNoMatch("http://www.test.com/testing/reg*ex/escape?", rule);
        assertNoMatch("http://www.test.com/testing/reg*ex/escape?test", rule);
    }

    private void assertStartsWith(
            String startsWith, ReferenceFilter robotRule) {
        var rule = StringUtils.substring(
                robotRule.toString(), 0, startsWith.length());
        Assertions.assertEquals(startsWith, rule);
    }

    private void assertMatch(
            String url, ReferenceFilter robotRule, Boolean match) {
        var regexFilter = (GenericReferenceFilter) robotRule;
        Assertions.assertEquals(
                match,
                url.matches(
                        regexFilter.getConfiguration()
                                .getValueMatcher().getPattern()));
    }

    private void assertMatch(
            String url, ReferenceFilter robotRule) {
        assertMatch(url, robotRule, true);
    }

    private void assertNoMatch(
            String url, ReferenceFilter robotRule) {
        assertMatch(url, robotRule, false);
    }

    private List<RobotsTxtFilter> parseRobotRule(
            String agent, String content, String url) throws IOException {
        var filters = new ArrayList<RobotsTxtFilter>();
        var robotProvider = new StandardRobotsTxtProvider();

        var robotsTxt = robotProvider.parseRobotsTxt(
                IOUtils.toInputStream(content, UTF_8), url, agent);
        filters.addAll(robotsTxt.getDisallowFilters());
        filters.addAll(robotsTxt.getAllowFilters());
        return filters;
    }

    private List<RobotsTxtFilter> parseRobotRule(String agent, String content)
            throws IOException {
        return parseRobotRule(
                agent, content,
                "http://www.test.com/some/fake/url.html");
    }
}
