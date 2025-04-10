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
package com.norconex.crawler.web.doc.operations.canon.impl;

import static org.apache.commons.lang3.StringUtils.substringBefore;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.web.doc.operations.canon.CanonicalLinkDetector;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Generic canonical link detector. It detects links from the HTTP headers
 * as well as HTML files.  Good canonical reference documentation can be found
 * on this <a href="https://support.google.com/webmasters/answer/139066">
 * Google Webmaster Tools help page</a>.</p>
 *
 * <h2>HTTP Headers</h2>
 * <p>This detector will look for a metadata field (normally obtained
 * from the HTTP Headers) name called "Link" with a
 * value following this pattern:</p>
 * <pre>
 * &lt;http://www.example.com/sample.pdf&gt; rel="canonical"
 * </pre>
 * <p>All documents will be verified for a canonical link (not just HTML).</p>
 *
 * <h2>Document content</h2>
 * <p>This detector will look within the HTML &lt;head&gt; tags for a
 * &lt;link&gt; tag following this pattern:</p>
 * <pre>
 * &lt;link rel="canonical" href="https://www.example.com/sample" /&gt;
 * </pre>
 * <p>Only HTML documents will be verified for a canonical link.  By default,
 * these content-types are considered HTML:</p>
 * <pre>
 * text/html, application/xhtml+xml, vnd.wap.xhtml+xml, x-asp
 * </pre>
 * <p>You can specify your own content types as long as they contain HTML
 * text.
 * </p>
 * <p>
 * The above example ignores canonical link resolution.
 * </p>
 *
 * @since 2.2.0
 */
@EqualsAndHashCode
@ToString
public class GenericCanonicalLinkDetector
        implements CanonicalLinkDetector,
        Configurable<GenericCanonicalLinkDetectorConfig> {

    private static final List<ContentType> DEFAULT_CONTENT_TYPES =
            Collections.unmodifiableList(
                    Arrays.asList(
                            ContentType.HTML,
                            ContentType.valueOf("application/xhtml+xml"),
                            ContentType.valueOf("vnd.wap.xhtml+xml"),
                            ContentType.valueOf("x-asp")));

    private final GenericCanonicalLinkDetectorConfig configuration =
            new GenericCanonicalLinkDetectorConfig(DEFAULT_CONTENT_TYPES);

    @Override
    public GenericCanonicalLinkDetectorConfig getConfiguration() {
        return configuration;
    }

    @Override
    public String detectFromMetadata(String reference, Properties metadata) {
        var link = StringUtils.trimToNull(metadata.getString("Link"));
        if (link != null) {
            link = link.replaceAll("\\s+", " ");
            // There might be multiple "links" in the same Link string.
            // We process them all individually.
            return Stream.of(StringUtils.split(link, '<'))
                    .filter(lnk -> Pattern.compile(
                            "(?i)\\brel\\s?=\\s?([\"'])\\s?canonical\\s?\\1")
                            .matcher(lnk).find())
                    .findFirst()
                    .map(lnk -> Pattern.compile("^([^>]+)>").matcher(lnk))
                    .filter(Matcher::find)
                    .map(matcher -> toAbsolute(
                            reference,
                            matcher.group(1).trim()))
                    .orElse(null);
        }
        return null;
    }

    @Override
    public String detectFromContent(
            String reference, InputStream is, ContentType contentType)
            throws IOException {
        var cTypes = configuration.getContentTypes();
        if (cTypes.isEmpty()) {
            cTypes = DEFAULT_CONTENT_TYPES;
        }

        // Do not extract if not a supported content type
        if (!cTypes.contains(contentType)) {
            return null;
        }

        try (var scanner = new Scanner(is)) {
            scanner.useDelimiter("<");
            while (scanner.hasNext()) {
                var tag = substringBefore(scanner.next().trim(), ">")
                        .replaceAll("\\s+", " ");
                // if we are past the HTML "head" section, we are done
                if (EqualsUtil.equalsAnyIgnoreCase(
                        tag.replaceFirst("^(\\w+)", "$1"), "body", "/head")) {
                    return null;
                }

                if (Pattern.compile(
                        "(?i)\\brel\\s?=\\s?([\"'])\\s?canonical\\s?\\1")
                        .matcher(tag).find()) {
                    var matcher = Pattern.compile(
                            "(?i)\\bhref\\s?=\\s?([\"'])(.*?)\\s?\\1")
                            .matcher(tag);
                    if (matcher.find()) {
                        return toAbsolute(reference, matcher.group(2));
                    }
                }
            }
        }
        return null;
    }

    private String toAbsolute(String pageReference, String link) {
        if (link == null) {
            return null;
        }
        if (link.matches("^https?://")) {
            return link;
        }

        return HttpURL.toAbsolute(
                pageReference, StringEscapeUtils.unescapeHtml4(link));
    }
}
