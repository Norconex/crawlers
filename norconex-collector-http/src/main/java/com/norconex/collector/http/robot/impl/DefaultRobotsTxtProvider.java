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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.CoreProtocolPNames;
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
 *     class="com.norconex.collector.http.handler.DefaultRobotsTxtProvider"/&gt;
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
            DefaultHttpClient httpClient, String url) {
        String baseURL = getBaseURL(url);
        RobotsTxt robotsTxt = robotsTxtCache.get(baseURL);
        if (robotsTxt != null) {
            return robotsTxt;
        }
        
        String userAgent = ((String) httpClient.getParams().getParameter(
                CoreProtocolPNames.USER_AGENT)).toLowerCase();
        String robotsURL = baseURL + "/robots.txt";
        HttpGet method = new HttpGet(robotsURL);
        List<String> sitemapLocations = new ArrayList<String>();
        List<IURLFilter> filters = new ArrayList<IURLFilter>();
        MutableFloat crawlDelay = 
                new MutableFloat(RobotsTxt.UNSPECIFIED_CRAWL_DELAY);
        try {
            HttpResponse response = httpClient.execute(method);
            InputStreamReader isr = 
                    new InputStreamReader(response.getEntity().getContent());
            BufferedReader br = new BufferedReader(isr);
            boolean agentAlreadyMatched = false;
            boolean doneWithAgent = false;
            String line;
            while ((line = br.readLine()) != null) {
                String key = line.replaceFirst("(.*?)(:.*)", "$1").trim();
                String value = line.replaceFirst("(.*?:)(.*)", "$2").trim();
                if ("sitemap".equalsIgnoreCase(key)) {
                    sitemapLocations.add(value);
                }
                if (!doneWithAgent) {
                    if ("user-agent".equalsIgnoreCase(key)) {
                        if (matchesUserAgent(userAgent, value)) {
                            agentAlreadyMatched = true;
                        } else if (agentAlreadyMatched) {
                            doneWithAgent = true;
                        }
                    }
                    if (agentAlreadyMatched) {
                        parseAgentLines(
                                baseURL, filters, crawlDelay, key, value);
                    }
                }
            }
            isr.close();
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.info("Not able to obtain robots.txt at: " + robotsURL, e);
            } else {
                LOG.info("Not able to obtain robots.txt at: " + robotsURL);
            }
        }
        
        robotsTxt = new RobotsTxt(
                filters.toArray(new IURLFilter[]{}), crawlDelay.floatValue());
        robotsTxtCache.put(baseURL, robotsTxt);
        return robotsTxt;
    }

    private void parseAgentLines(String baseURL, List<IURLFilter> filters,
            MutableFloat crawlDelay, String key, String value) {
        if ("disallow".equalsIgnoreCase(key)) {
            filters.add(buildURLFilter(
            		baseURL, value, OnMatch.EXCLUDE));
        } else if ("allow".equalsIgnoreCase(key)) {
            filters.add(buildURLFilter(
            		baseURL, value, OnMatch.INCLUDE));
        } else if ("crawl-delay".equalsIgnoreCase(key)) {
            crawlDelay.setValue(NumberUtils.toFloat(
                    value, crawlDelay.floatValue()));
        } else if ("sitemap".equalsIgnoreCase(key)) {
            //TODO implement me.
            LOG.warn("Sitemap in robots.txt encountered. "
                   + "CURRENTLY NOT SUPPORTED.");
        }
    }
    
    private boolean matchesUserAgent(
            String userAgent, String value) {
        return "*".equals(value) 
                || userAgent.contains(value.toLowerCase());
    }
    
    private String getBaseURL(String url) {
        String baseURL = url.replaceFirst("(.*?://.*?/)(.*)", "$1");
        if (baseURL.endsWith("/")) {
            baseURL = StringUtils.removeEnd(baseURL, "/");
        }
        return baseURL;
    }
    
    private IURLFilter buildURLFilter(
            String baseURL, final String path, final OnMatch onMatch) {
        String regex = path;
        regex = regex.replaceAll("\\*", ".*");
        if (!regex.endsWith("$")) {
            regex += ".*";
        }
        regex = baseURL + regex;
        RegexURLFilter filter = new RegexURLFilter(regex, onMatch, false) {
            private static final long serialVersionUID = -5051322223143577684L;
            @Override
            public String toString() {
                return "Robots.txt (" 
                		+ (onMatch == OnMatch.INCLUDE ? "Allow:" : "Disallow:")
                        + path + ")";
            }
        };
        return filter;
    }
}
