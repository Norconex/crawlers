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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.filter.impl.RegexURLFilter;
import com.norconex.collector.http.robot.IRobotsTxtProvider;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.importer.filter.OnMatch;

/**
 * Default implementation of {@link IRobotsTxtProvider}.  
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;robotsTxt ignore="false" 
 *     class="com.norconex.collector.http.robot.DefaultRobotsTxtProvider"/&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class DefaultRobotsTxtProvider implements IRobotsTxtProvider {

    private static final long serialVersionUID = 1459917072724725590L;
    private static final Logger LOG = LogManager.getLogger(
            DefaultRobotsTxtProvider.class);
    
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
            robotsTxt = new RobotsTxt(new IURLFilter[]{});
        }
        robotsTxtCache.put(baseURL, robotsTxt);
        return robotsTxt;
    }

    
    public RobotsTxt parseRobotsTxt(
            InputStream is, String url, String userAgent) throws IOException {
        String baseURL = getBaseURL(url);
        
        //--- Load matching data ---
        InputStreamReader isr = new InputStreamReader(is);
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
            List<IURLFilter> filters = new ArrayList<IURLFilter>();
            for (String path : rules.keySet()) {
                String rule = rules.get(path);
                if ("disallow".equalsIgnoreCase(rule)) {
                    IURLFilter filter;
                    filter = buildURLFilter(baseURL, path, OnMatch.EXCLUDE);
                    LOG.debug("Add filter from robots.txt: " + filter.toString());
                    filters.add(filter);
                } else if ("allow".equalsIgnoreCase(rule)) {                    
                    IURLFilter filter;
                    filter = buildURLFilter(baseURL, path, OnMatch.INCLUDE);
                    LOG.debug("Add filter from robots.txt: " + filter.toString());
                    filters.add(filter);
                } 
            }
            float delay = NumberUtils.toFloat(
                    crawlDelay, RobotsTxt.UNSPECIFIED_CRAWL_DELAY);
            return new RobotsTxt(
                    filters.toArray(new IURLFilter[]{}),
                    sitemaps.toArray(ArrayUtils.EMPTY_STRING_ARRAY),
                    delay);
        }
        private IURLFilter buildURLFilter(
                String baseURL, final String path, final OnMatch onMatch) {
            String regex = path;
            regex = regex.replaceAll("\\*", ".*");
            if (!regex.endsWith("$")) {
                regex += "?.*";
            }
            regex = baseURL + regex;
            RegexURLFilter filter = new RegexURLFilter(regex, onMatch, false) {
                private static final long serialVersionUID = 
                        -5051322223143577684L;
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
