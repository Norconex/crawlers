/* Copyright 2020-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.link.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.CommonMatchers;
import com.norconex.importer.util.DomUtil;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link DomLinkExtractor}.
 * </p>
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class DomLinkExtractorConfig {

    public static final List<String> DEFAULT_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("http", "https", "ftp"));

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LinkSelector {
        private String selector;
        private String extract;
    }

    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default is
     * {@link CommonMatchers#DOM_CONTENT_TYPES}.
     */
    private final TextMatcher contentTypeMatcher =
            CommonMatchers.domContentTypes();

    /**
     * Matcher of one or more fields to use as the source of content to
     * extract links from, instead of the document content.
     */
    private final TextMatcher fieldMatcher = new TextMatcher();

    private final List<LinkSelector> linkSelectors = new ArrayList<>();
    private final List<String> extractSelectors = new ArrayList<>();
    private final List<String> noExtractSelectors = new ArrayList<>();

    /**
     * The assumed source character encoding.
     */
    private Charset charset;
    /**
     * The parser to use when creating the DOM-tree.
     */
    private String parser = DomUtil.PARSER_HTML;
    private boolean ignoreNofollow;
    /**
     * Whether to ignore extra data associated with a link.
     */
    private boolean ignoreLinkData;
    private final List<String> schemes = new ArrayList<>(DEFAULT_SCHEMES);

    public DomLinkExtractorConfig() {
        addLinkSelector("a[href]", "attr(href)");
        addLinkSelector("[src]", "attr(src)");
        addLinkSelector("link[href]", "attr(href)");
        addLinkSelector("meta[http-equiv='refresh']", "attr(content)");
    }

    public DomLinkExtractorConfig setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        return this;
    }

    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default is
     * {@link CommonMatchers#HTML_CONTENT_TYPES}.
     * @param matcher content type matcher
     * @return this
     */
    public DomLinkExtractorConfig setContentTypeMatcher(TextMatcher matcher) {
        contentTypeMatcher.copyFrom(matcher);
        return this;
    }

    /**
     * Adds a new link selector extracting the "text" from matches.
     * @param selector JSoup selector
     * @return this
     */
    public DomLinkExtractorConfig addLinkSelector(String selector) {
        addLinkSelector(selector, null);
        return this;
    }

    public DomLinkExtractorConfig addLinkSelector(
            String selector, String extract) {
        linkSelectors.add(
                new LinkSelector(
                        trim(selector),
                        isBlank(extract) ? "text" : trim(extract)));
        return this;
    }

    public DomLinkExtractorConfig removeLinkSelector(String selector) {
        linkSelectors.removeIf(
                ls -> Objects.equals(ls.getSelector(), trim(selector)));
        return this;
    }

    public DomLinkExtractorConfig clearLinkSelectors() {
        linkSelectors.clear();
        return this;
    }

    public List<String> getExtractSelectors() {
        return Collections.unmodifiableList(extractSelectors);
    }

    /**
     * Only apply link selectors to portions of a document
     * matching the supplied selectors.
     * @param selectors the CSS selectors
     * @return this
     */
    public DomLinkExtractorConfig setExtractSelectors(List<String> selectors) {
        CollectionUtil.setAll(extractSelectors, selectors);
        return this;
    }

    public DomLinkExtractorConfig addExtractSelectors(List<String> selectors) {
        extractSelectors.addAll(selectors);
        return this;
    }

    public List<String> getNoExtractSelectors() {
        return Collections.unmodifiableList(noExtractSelectors);
    }

    /**
     * Do not apply link selectors to portions of a document
     * matching the supplied selectors.
     * @param selectors the CSS selectors
     * @return this
     */
    public DomLinkExtractorConfig setNoExtractSelectors(
            List<String> selectors) {
        CollectionUtil.setAll(noExtractSelectors, selectors);
        return this;
    }

    public DomLinkExtractorConfig addNoExtractSelectors(
            List<String> selectors) {
        noExtractSelectors.addAll(selectors);
        return this;
    }

    /**
     * Gets the schemes to be extracted.
     * @return schemes to be extracted
     */
    public List<String> getSchemes() {
        return Collections.unmodifiableList(schemes);
    }

    /**
     * Sets the schemes to be extracted.
     * @param schemes schemes to be extracted
     * @return this
     */
    public DomLinkExtractorConfig setSchemes(List<String> schemes) {
        CollectionUtil.setAll(this.schemes, schemes);
        return this;
    }
}
