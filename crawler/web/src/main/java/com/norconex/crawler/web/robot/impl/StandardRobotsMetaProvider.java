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
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.web.robot.RobotsMeta;
import com.norconex.crawler.web.robot.RobotsMetaProvider;
import com.norconex.crawler.web.util.Web;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

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
 *  <robotsMeta
 *     class="com.norconex.crawler.web.robot.impl.StandardRobotsMetaProvider">
 *     <headersPrefix>(string prefixing headers)</headersPrefix>
 *  </robotsMeta>
 * }
 *
 * {@nx.xml.example
 * <robotsMeta />
 * }
 * <p>
 * The above example ignores robot meta information.
 * </p>
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class StandardRobotsMetaProvider implements
        RobotsMetaProvider, Configurable<StandardRobotsMetaProviderConfig> {

    @Getter
    private final StandardRobotsMetaProviderConfig configuration =
            new StandardRobotsMetaProviderConfig();

    @Override
    public RobotsMeta getRobotsMeta(
            Reader document,
            String documentUrl,
            ContentType contentType,
            Properties httpHeaders) throws IOException {

        RobotsMeta robotsMeta = null;

        //--- Find in page content ---
        if (isMetaSupportingContentType(contentType)) {
            try (var reader = new TextReader(document)) {
                String text = null;
                while ((text = reader.readText()) != null) {
                    // Normalize spaces
                    var cleanText = text
                            .replaceAll("\\s+", " ")
                            .replace("< ", "<")
                            .replace(" >", ">")
                            .replace("</ ", "</")
                            .replace("/ >", "/>");

                    // Eliminate comments
                    cleanText = cleanText.replaceAll(
                            "(?s)(<!--.*?-->)|(<!--.+?-->)|(<!--.*$)", "");
                    var robotContent = findInContent(cleanText);
                    robotsMeta = buildMeta(robotContent);
                    if (robotsMeta != null) {
                        LOG.debug("Meta robots \"{}\" found in HTML meta "
                                + "tag for: {}", robotContent, documentUrl);
                    }
                    if (robotsMeta != null || isEndOfHead(cleanText)) {
                        break;
                    }
                }
            }
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

    private boolean isMetaSupportingContentType(ContentType contentType) {
        return contentType != null && contentType.equals(ContentType.HTML);
    }

    private RobotsMeta findInHeaders(
            Properties httpHeaders, String documentUrl) {
        var name = "X-Robots-Tag";
        if (StringUtils.isNotBlank(configuration.getHeadersPrefix())) {
            name = configuration.getHeadersPrefix() + name;
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
        var m = Pattern.compile("(?is)<meta\\s[^<>]+>").matcher(text);
        while (m.find()) {
            var props = Web.parseDomAttributes(m.group(), true);
            if ("robots".equalsIgnoreCase(StringUtils.trimToEmpty(
                    props.getString("name")))) {
                var content = props.getString("content");
                if (StringUtils.isNotBlank(content)) {
                    return content;
                }
            }
        }
        return null;
    }

    private boolean isEndOfHead(String line) {
        return line.matches("(?is)<\\s*/\\s*HEAD\\s*>");
    }
}
