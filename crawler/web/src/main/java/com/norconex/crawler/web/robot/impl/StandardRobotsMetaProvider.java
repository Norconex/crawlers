/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.crawler.web.robot.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.web.robot.RobotsMeta;
import com.norconex.crawler.web.robot.RobotsMetaProvider;

/**
 * <p>Implementation of {@link RobotsMetaProvider} as per X-Robots-Tag
 * and ROBOTS standards.
 * Extracts robots information from "ROBOTS" meta tag in an HTML page
 * or "X-Robots-Tag" tag in the HTTP header (see
 * <a href="https://developers.google.com/webmasters/control-crawl-index/docs/robots_meta_tag">
 * https://developers.google.com/webmasters/control-crawl-index/docs/robots_meta_tag</a>
 * and
 * <a href="http://www.robotstxt.org/meta.html">
 * http://www.robotstxt.org/meta.html</a>).
 * </p>
 *
 * <p>If you specified a prefix for the HTTP headers, make sure to specify it
 * again here or the robots meta tags will not be found.</p>
 *
 * <p>If robots instructions are provided in both the HTML page and
 * HTTP header, the ones in HTML page will take precedence, and the
 * ones in HTTP header will be ignored.</p>
 *
 * {@nx.xml.usage
 *  <robotsMeta ignore="false"
 *     class="com.norconex.crawler.web.robot.impl.StandardRobotsMetaProvider">
 *     <headersPrefix>(string prefixing headers)</headersPrefix>
 *  </robotsMeta>
 * }
 *
 * {@nx.xml.example
 * <robotsMeta ignore="true" />
 * }
 * <p>
 * The above example ignores robot meta information.
 * </p>
 *
 */
public class StandardRobotsMetaProvider
        implements RobotsMetaProvider, XMLConfigurable {

    private static final Logger LOG = LoggerFactory.getLogger(
            StandardRobotsMetaProvider.class);
    private static final Pattern META_ROBOTS_PATTERN = Pattern.compile(
            "<\\s*META[^>]*?NAME\\s*=\\s*[\"']{0,1}\\s*robots"
                    + "\\s*[\"']{0,1}\\s*[^>]*?>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_CONTENT_PATTERN = Pattern.compile(
            "\\s*CONTENT\\s*=\\s*[\"']{0,1}([\\s\\w,]+)[\"']{0,1}\\s*[^>]*?>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HEAD_PATTERN = Pattern.compile(
            "<\\s*/\\s*HEAD\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COMMENT_PATTERN = Pattern.compile("<!--.*?-->",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private String headersPrefix;

    @Override
    public RobotsMeta getRobotsMeta(Reader document, String documentUrl,
           ContentType contentType, Properties httpHeaders) throws IOException {

        RobotsMeta robotsMeta = null;

        //--- Find in page content ---
        if (isMetaSupportingContentType(contentType)) {
            var reader = new TextReader(document);
            String text = null;
            while ((text = reader.readText()) != null) {
                // First eliminate comments
                var clean = COMMENT_PATTERN.matcher(text).replaceAll("");
                var robotContent = findInContent(clean);
                if (robotContent != null) {
                    robotsMeta = buildMeta(robotContent);
                    if (robotsMeta != null) {
                        LOG.debug("Meta robots \"{}\" found in HTML meta tag "
                                + "for: {}", robotContent, documentUrl);
                    }
                    break;
                }
                if (isEndOfHead(clean)) {
                    break;
                }
            }
            reader.close();
        }

        //--- Find in HTTP header ---
        if (robotsMeta == null) {
            robotsMeta = findInHeaders(httpHeaders, documentUrl);
        }

        if (robotsMeta == null) {
            LOG.debug("No meta robots found for: {}", documentUrl);
        }

        return robotsMeta;
    }

    public String getHeadersPrefix() {
        return headersPrefix;
    }
    public void setHeadersPrefix(String headersPrefix) {
        this.headersPrefix = headersPrefix;
    }

    private boolean isMetaSupportingContentType(ContentType contentType) {
        return contentType != null && contentType.equals(ContentType.HTML);
    }

    private RobotsMeta findInHeaders(
            Properties httpHeaders, String documentUrl) {
        var name = "X-Robots-Tag";
        if (StringUtils.isNotBlank(headersPrefix)) {
            name = headersPrefix + name;
        }
        var content = httpHeaders.getString(name);
        var robotsMeta = buildMeta(content);
        if (LOG.isDebugEnabled() && robotsMeta != null) {
            LOG.debug("Meta robots \"{}\" found in HTTP header for: {}",
                    content, documentUrl);
        }
        return robotsMeta;
    }

    private RobotsMeta buildMeta(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        var rules = StringUtils.split(content, ',');
        var noindex = false;
        var nofollow = false;
        for (String rule : rules) {
            if ("noindex".equalsIgnoreCase(rule.trim())) {
                noindex = true;
            }
            if ("nofollow".equalsIgnoreCase(rule.trim())) {
                nofollow = true;
            }
        }
        return new RobotsMeta(nofollow, noindex);
    }


    private String findInContent(String text) {
        var rmatcher = META_ROBOTS_PATTERN.matcher(text);
        while (rmatcher.find()) {
            var robotTag = rmatcher.group();
            var cmatcher = META_CONTENT_PATTERN.matcher(robotTag);
            if (cmatcher.find()) {
                var content = cmatcher.group(1);
                if (StringUtils.isNotBlank(content)) {
                    return content;
                }
            }
        }
        return null;
    }

    private boolean isEndOfHead(String line) {
        return HEAD_PATTERN.matcher(line).matches();
    }

    @Override
    public void loadFromXML(XML xml) {
        setHeadersPrefix(xml.getString("headersPrefix", headersPrefix));
    }

    @Override
    public void saveToXML(XML xml) {
        xml.addElement("headersPrefix", headersPrefix);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this,
                ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
