/* Copyright 2017-2024 Norconex Inc.
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
package com.norconex.crawler.web.commands.crawl.task.operations.link.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.web.commands.crawl.task.operations.link.Link;
import com.norconex.crawler.web.commands.crawl.task.operations.link.LinkExtractor;
import com.norconex.crawler.web.doc.WebDocMetadata;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Link extractor using regular expressions to extract links found in text
 * documents. Relative links are resolved to the document URL.
 * For HTML documents, it is best advised to use the
 * {@link HtmlLinkExtractor} or {@link DomLinkExtractor},
 * which addresses many cases specific to HTML.
 * </p>
 *
 * <h3>Applicable documents</h3>
 * <p>
 * By default, this extractor will extract URLs only in documents having
 * their content type matching this regular expression:
 * </p>
 * <pre>
 * text/.*
 * </pre>
 * <p>
 * You can specify your own restrictions using
 * {@link RegexLinkExtractorConfig#getRestrictions()},
 * but make sure they represent text files.
 * </p>
 *
 * <h3>Referrer data</h3>
 * <p>
 * The following referrer information is stored as metadata in each document
 * represented by the extracted URLs:
 * </p>
 * <ul>
 *   <li><b>Referrer reference:</b> The reference (URL) of the page where the
 *   link to a document was found.  Metadata value is
 *   {@link WebDocMetadata#REFERRER_REFERENCE}.</li>
 * </ul>
 *
 * <h3>Character encoding</h3>
 * <p>This extractor will by default <i>attempt</i> to
 * detect the encoding of the a page when extracting links and
 * referrer information. If no charset could be detected, it falls back to
 * UTF-8. It is also possible to dictate which encoding to use with
 * {@link RegexLinkExtractorConfig#setCharset(java.nio.charset.Charset)}.
 * </p>
 *
 * @since 2.7.0
 */
@EqualsAndHashCode
@ToString
public class RegexLinkExtractor
        implements LinkExtractor, Configurable<RegexLinkExtractorConfig> {

    //TODO make buffer size and overlap size configurable
    //1MB: make configurable
    static final int MAX_BUFFER_SIZE = 1024 * 1024;
    // max url leng is 2048 x 2 bytes x 2 for <a> anchor attributes.
    static final int OVERLAP_SIZE = 2 * 2 * 2048;

    @Getter
    private final RegexLinkExtractorConfig configuration =
            new RegexLinkExtractorConfig();

    @Override
    public Set<Link> extractLinks(CrawlDoc doc) throws IOException {

        Set<Link> links = new HashSet<>();

        if (!getConfiguration().getRestrictions().isEmpty()
                && !getConfiguration().getRestrictions().matches(
                        doc.getMetadata())) {
            return Collections.emptySet();
        }

        if (configuration.getFieldMatcher().isSet()) {
            // Fields
            doc.getMetadata()
                    .matchKeys(configuration.getFieldMatcher())
                    .valueList()
                    .forEach(val -> extractLinks(
                            links, val, doc.getReference()));
        } else {
            // Body
            var sb = new StringBuilder();
            int ch;
            try (var reader = new TextReader(
                    new InputStreamReader(doc.getInputStream()))) {
                while ((ch = reader.read()) != -1) {
                    sb.append((char) ch);
                    if (sb.length() >= MAX_BUFFER_SIZE) {
                        var content = sb.toString();
                        extractLinks(links, content, doc.getReference());
                        sb.delete(0, sb.length() - OVERLAP_SIZE);
                    }
                }
            }
            var content = sb.toString();
            extractLinks(links, content, doc.getReference());
            sb.setLength(0);
        }
        return links;
    }

    private void extractLinks(
            Set<Link> links, String content, String referrer) {
        configuration.getPatterns().forEach(p -> {
            var pattern = p.getMatch();
            var repl = p.getReplace();
            var matcher = Pattern.compile(pattern).matcher(content);
            while (matcher.find()) {
                var url = matcher.group();
                if (StringUtils.isNotBlank(repl)) {
                    url = url.replaceFirst(pattern, repl);
                }
                url = HttpURL.toAbsolute(referrer, url);
                if (url == null) {
                    continue;
                }
                var link = new Link(url);
                link.setReferrer(referrer);
                links.add(link);
            }
        });
    }
}
