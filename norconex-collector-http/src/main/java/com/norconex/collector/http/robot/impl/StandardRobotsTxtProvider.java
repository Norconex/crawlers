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
package com.norconex.collector.http.robot.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.filter.IReferenceFilter;
import com.norconex.collector.core.filter.impl.RegexReferenceFilter;
import com.norconex.collector.http.robot.IRobotsTxtProvider;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * Implementation of {@link IRobotsTxtProvider} as per the robots.txt standard
 * described at <a href="http://www.robotstxt.org/robotstxt.html">
 * http://www.robotstxt.org/robotstxt.html</a>.
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;robotsTxt ignore="false" 
 *     class="com.norconex.collector.http.robot.StandardRobotsTxtProvider"/&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class StandardRobotsTxtProvider implements IRobotsTxtProvider {

    private static final Logger LOG = LogManager.getLogger(
            StandardRobotsTxtProvider.class);
    
    private Map<String, RobotsTxt> robotsTxtCache =
            new HashMap<String, RobotsTxt>();

    @Override
    public synchronized RobotsTxt getRobotsTxt(
            HttpClient httpClient, String url, String userAgent) {
        
        String baseURL = getBaseURL(url);
        RobotsTxt robotsTxt = robotsTxtCache.get(baseURL);
        if (robotsTxt != null) {
            return robotsTxt;
        }

        String robotsURL = baseURL + "/robots.txt";
        HttpGet method = new HttpGet(robotsURL);
        try {
            HttpResponse response = httpClient.execute(method);
            InputStream is = response.getEntity().getContent();
            robotsTxt = parseRobotsTxt(is, url, userAgent);
        } catch (Exception e) {
            LOG.warn("Not able to obtain robots.txt at: " + robotsURL, e);
            robotsTxt = new RobotsTxt(new IReferenceFilter[]{});
        }
        robotsTxtCache.put(baseURL, robotsTxt);
        return robotsTxt;
    }

    
    /*default*/ RobotsTxt parseRobotsTxt(
            InputStream is, String url, String userAgent) throws IOException {
        String baseURL = getBaseURL(url);
        
        //--- Load matching data ---
        InputStreamReader isr = new InputStreamReader(is, CharEncoding.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        RobotData data = new RobotData();
        boolean parse = false;
        String line;
        while ((line = br.readLine()) != null) {
            
            if (ignoreLine(line))continue;
            line = cleanLineFromComments(line);
            
            String key = line.replaceFirst("(.*?)(:.*)", "$1").trim();
            String value = line.replaceFirst("(.*?:)(.*)", "$2").trim();
            
            if ("sitemap".equalsIgnoreCase(key)) {
                data.sitemaps.add(value);
            }
            if ("user-agent".equalsIgnoreCase(key)) {
                // stop parsing if we have an exact match already
                if (data.precision == RobotData.Precision.EXACT) {
                    break;
                }
                RobotData.Precision precision = 
                        matchesUserAgent(userAgent, value);
                if (precision.ordinal() > data.precision.ordinal()) {
                    data.clear();
                    data.precision = precision;
                    parse = true;
                } else {
                    parse = false;
                }
            } else if (parse) {
                if ("crawl-delay".equalsIgnoreCase(key)) {
                    data.crawlDelay = value;
                } else {
                    data.rules.put(value, key);
                }
            }
        }
        isr.close();
        
        return data.toRobotsTxt(baseURL);
    }

    private String cleanLineFromComments(String line) {
        if (line.matches(".*\\s+#.*")){
            line = line.replaceFirst("\\s+#.*", "");
        }
        return line;
    }

    private boolean ignoreLine(String line) {
        if (line.matches("\\s*#.*") || line.equals("Allow: /")) {
            return true;
        }
        return false;
    }
    
    private RobotData.Precision matchesUserAgent(
            String userAgent, String value) {
        if ("*".equals(value)) {
            return RobotData.Precision.WILD;
        }
        if (StringUtils.equalsIgnoreCase(userAgent, value)) {
            return RobotData.Precision.EXACT;
        }
        if (value.endsWith("*")) {
            String val = StringUtils.removeEnd(value, "*");
            if (StringUtils.startsWithIgnoreCase(userAgent, val)) {
                return RobotData.Precision.PARTIAL;
            }
        }
        if (StringUtils.containsIgnoreCase(userAgent, value)) {
            return RobotData.Precision.PARTIAL;
        }
        return RobotData.Precision.NOMATCH;
    }
    
    private String getBaseURL(String url) {
        String baseURL = url.replaceFirst("(.*?://.*?/)(.*)", "$1");
        if (baseURL.endsWith("/")) {
            baseURL = StringUtils.removeEnd(baseURL, "/");
        }
        return baseURL;
    }
    
    private static class RobotData {
        private enum Precision { 
            NOMATCH, WILD, PARTIAL, EXACT;
        };
        private Precision precision = Precision.NOMATCH;
        private Map<String, String> rules = 
                new ListOrderedMap<String, String>();
        private List<String> sitemaps = new ArrayList<String>();
        private String crawlDelay;
        private void clear() {
            sitemaps.clear();
            crawlDelay = null;
        }
        private RobotsTxt toRobotsTxt(String baseURL) {
            List<IReferenceFilter> filters = new ArrayList<IReferenceFilter>();
            for (String path : rules.keySet()) {
                String rule = rules.get(path);
                if ("disallow".equalsIgnoreCase(rule)) {
                    IReferenceFilter filter;
                    filter = buildURLFilter(baseURL, path, OnMatch.EXCLUDE);
                    LOG.debug("Add filter from robots.txt: " + filter.toString());
                    filters.add(filter);
                } else if ("allow".equalsIgnoreCase(rule)) {                    
                    IReferenceFilter filter;
                    filter = buildURLFilter(baseURL, path, OnMatch.INCLUDE);
                    LOG.debug("Add filter from robots.txt: " + filter.toString());
                    filters.add(filter);
                } 
            }
            float delay = NumberUtils.toFloat(
                    crawlDelay, RobotsTxt.UNSPECIFIED_CRAWL_DELAY);
            return new RobotsTxt(
                    filters.toArray(new IReferenceFilter[]{}),
                    sitemaps.toArray(ArrayUtils.EMPTY_STRING_ARRAY),
                    delay);
        }
        private IReferenceFilter buildURLFilter(
                String baseURL, final String path, final OnMatch onMatch) {
            String regex = path;
            regex = regex.replaceAll("\\*", ".*");
            if (!regex.endsWith("$")) {
                regex += "?.*";
            }
            regex = baseURL + regex;
            RegexReferenceFilter filter = new RegexReferenceFilter(
                    regex, onMatch, false) {
                @Override
                public String toString() {
                    return "Robots.txt ("
                            + (onMatch == OnMatch.INCLUDE 
                            ? "Allow:" : "Disallow:") + path + ")";
                }
            };
            return filter;
        }
    }
}

