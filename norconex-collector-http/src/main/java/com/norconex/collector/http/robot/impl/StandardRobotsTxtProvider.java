/* Copyright 2010-2017 Norconex Inc.
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.filter.IReferenceFilter;
import com.norconex.collector.core.filter.impl.RegexReferenceFilter;
import com.norconex.collector.http.robot.IRobotsTxtFilter;
import com.norconex.collector.http.robot.IRobotsTxtProvider;
import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * <p>
 * Implementation of {@link IRobotsTxtProvider} as per the robots.txt standard
 * described at <a href="http://www.robotstxt.org/robotstxt.html">
 * http://www.robotstxt.org/robotstxt.html</a>.
 * </p>
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;robotsTxt ignore="false" 
 *     class="com.norconex.collector.http.robot.impl.StandardRobotsTxtProvider"/&gt;
 * </pre>
 * 
 * <h4>Usage example:</h4>
 * <p>
 * The following ignores "robots.txt" files present on web sites.
 * </p>
 * <pre>
 *  &lt;robotsTxt ignore="true" /&gt;
 * </pre>
 * 
 * @author Pascal Essiembre
 */
public class StandardRobotsTxtProvider implements IRobotsTxtProvider {

    private static final Logger LOG = LogManager.getLogger(
            StandardRobotsTxtProvider.class);
    
    private Map<String, RobotsTxt> robotsTxtCache = new HashMap<>();

    @Override
    public synchronized RobotsTxt getRobotsTxt(
            HttpClient httpClient, String url, String userAgent) {
        
        String trimmedURL = StringUtils.trimToEmpty(url);
        String baseURL = getBaseURL(trimmedURL);
        RobotsTxt robotsTxt = robotsTxtCache.get(baseURL);
        if (robotsTxt != null) {
            return robotsTxt;
        }

        String robotsURL = baseURL + "/robots.txt";
        try {
            HttpGet method = new HttpGet(robotsURL);
            HttpResponse response = httpClient.execute(method);
            InputStream is = response.getEntity().getContent();
            robotsTxt = parseRobotsTxt(is, trimmedURL, userAgent);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Fetched and parsed robots.txt: " + robotsURL);
            }
        } catch (Exception e) {
            LOG.warn("Not able to obtain robots.txt at: " + robotsURL, e);
            robotsTxt = new RobotsTxt(new IRobotsTxtFilter[]{});
        }
        robotsTxtCache.put(baseURL, robotsTxt);
        return robotsTxt;
    }

    protected RobotsTxt parseRobotsTxt(
            InputStream is, String url, String userAgent) throws IOException {
        String baseURL = getBaseURL(url);
        
        //--- Load matching data ---
        InputStreamReader isr = 
                new InputStreamReader(is, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(isr);
        RobotData data = new RobotData();
        boolean parse = false;
        String line;
        while ((line = br.readLine()) != null) {
            line = cleanLineFromTrailingComments(line);
            if (ignoreLine(line)) {
                continue;
            }
            
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
                } else if (StringUtils.isNotBlank(value)) {
                    data.rules.put(value, key);
                }
            }
        }
        isr.close();
        
        return data.toRobotsTxt(baseURL);
    }

    private String cleanLineFromTrailingComments(String line) {
        if (line.matches(".*\\s+#.*")){
            line = line.replaceFirst("\\s+#.*", "");
        }
        return line;
    }

    private static final Pattern PATTERN_COMMENT = Pattern.compile("\\s*#.*");
    private static final Pattern PATTERN_ALLOW_ALL = Pattern.compile(
            "\\s*allow\\s*:\\s*/\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_DISALLOW_NONE = Pattern.compile(
            "\\s*disallow\\s*:\\s*", Pattern.CASE_INSENSITIVE);
    private boolean ignoreLine(String line) {
        if (StringUtils.isBlank(line)) {
            return true;
        }
        if (PATTERN_COMMENT.matcher(line).matches()) {
            return true;
        }
        if (PATTERN_ALLOW_ALL.matcher(line).matches()) {
            return true;
        }
        if (PATTERN_DISALLOW_NONE.matcher(line).matches()) {
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
        String baseURL = HttpURL.getRoot(url);
        if (StringUtils.endsWith(baseURL, "/")) {
            baseURL = StringUtils.removeEnd(baseURL, "/");
        }
        return baseURL;
    }
    
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof StandardRobotsTxtProvider)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .toString();
    }    

    private static class RobotData {
        private enum Precision { 
            NOMATCH, WILD, PARTIAL, EXACT;
        };
        private Precision precision = Precision.NOMATCH;
        private Map<String, String> rules = new ListOrderedMap<>();
        private List<String> sitemaps = new ArrayList<>();
        private String crawlDelay;
        private void clear() {
            sitemaps.clear();
            crawlDelay = null;
        }
        private RobotsTxt toRobotsTxt(String baseURL) {
            List<IReferenceFilter> filters = new ArrayList<>();
            for (String path : rules.keySet()) {
                String rule = rules.get(path);
                if ("disallow".equalsIgnoreCase(rule)) {
                    IReferenceFilter filter;
                    filter = buildURLFilter(baseURL, path, OnMatch.EXCLUDE);
                    LOG.debug("Add filter from robots.txt: " + filter);
                    filters.add(filter);
                } else if ("allow".equalsIgnoreCase(rule)) {                    
                    IRobotsTxtFilter filter;
                    filter = buildURLFilter(baseURL, path, OnMatch.INCLUDE);
                    LOG.debug("Add filter from robots.txt: " + filter);
                    filters.add(filter);
                } 
            }
            float delay = NumberUtils.toFloat(
                    crawlDelay, RobotsTxt.UNSPECIFIED_CRAWL_DELAY);
            return new RobotsTxt(
                    filters.toArray(new IRobotsTxtFilter[]{}),
                    sitemaps.toArray(ArrayUtils.EMPTY_STRING_ARRAY),
                    delay);
        }
        private IRobotsTxtFilter buildURLFilter(
                String baseURL, final String path, final OnMatch onMatch) {
            // Take the robots.txt pattern literally as it may include
            // characters (or character sequences) that would have special
            // meaning in a regular expression.
            String regex = Pattern.quote(path);

            // An asterisk within a robots.txt pattern should match any string.
            // Thus we transform it into the regular expression `.*`. As we
            // previously enclosed the pattern in \Q and \E in order for it to
            // be interpreted literally, we must also explicitly exclude the
            // `.*` from quoting.
            regex = regex.replace("*", "\\E.*\\Q");

            // A dollar sign at the end a robots.txt pattern means the same as
            // in a regular expression. That is, it matches the end of the
            // string. (Again, we take into account previous quoting).
            //
            // Note that the presence or absence of trailing slashes is ignored
            // (e.g. a path with a trailing slash also matches an anchored
            // pattern without a trailing slash).
            if (regex.endsWith("$\\E")) {
                regex = regex.replaceFirst("\\$\\\\E\\z", "\\\\E/?");
            }

            // If the robots.txt pattern was *not* anchored, we explicitly allow
            // any trailing character.
            else {
                regex += ".*";
            }

            // Last, we assemble the final regex by explicitly anchoring and
            // prepending the (quoted) baseUrl.
            regex = "\\A" + Pattern.quote(baseURL) + regex + "\\z";
            RobotsTxtFilter filter = new RobotsTxtFilter(path, regex, onMatch);
            return filter;
        }
    }

    private static class RobotsTxtFilter extends RegexReferenceFilter 
            implements IRobotsTxtFilter {
        private final String path;
        public RobotsTxtFilter(
                String path, String regex, OnMatch onMatch) {
            super(regex, onMatch, false);
            this.path = path;
        }
        public String getPath() {
            return path;
        }
        @Override
        public String toString() {
            return "Robots.txt -> " + (getOnMatch() == OnMatch.INCLUDE 
                    ? "Allow: " : "Disallow: ") + path
                            + " (" + getRegex().toString() + ")";            
        }
        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .appendSuper(super.hashCode())
                .append(path)
                .toHashCode();
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof RobotsTxtFilter)) {
                return false;
            }
            RobotsTxtFilter other = (RobotsTxtFilter) obj;
            return new EqualsBuilder()
                .appendSuper(super.equals(obj))
                .append(path, other.path)
                .isEquals();
        }        
    }
    
}

