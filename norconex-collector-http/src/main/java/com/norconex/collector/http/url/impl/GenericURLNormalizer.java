/* Copyright 2010-2020 Norconex Inc.
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
package com.norconex.collector.http.url.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.url.URLNormalizer;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Generic implementation of {@link IURLNormalizer} that should satisfy
 * most URL normalization needs.  This implementation relies on
 * {@link URLNormalizer}.  Please refer to it for complete documentation and
 * examples.
 * </p>
 * <p>
 * This class is in effect by default. To skip its usage, you
 * can explicitly set the URL Normalizer to <code>null</code> in the
 * {@link HttpCrawlerConfig}, or you can disable it using
 * {@link #setDisabled(boolean)}.
 * </p>
 * <p>
 * By default, this class removes the URL fragment and applies these
 * <a href="http://tools.ietf.org/html/rfc3986">RFC 3986</a>
 * normalizations:
 * </p>
 * <ul>
 *   <li>Converting the scheme and host to lower case</li>
 *   <li>Capitalizing letters in escape sequences</li>
 *   <li>Decoding percent-encoded unreserved characters</li>
 *   <li>Removing the default port</li>
 *   <li>Encoding non-URI characters</li>
 * </ul>
 * <p>
 * To overwrite this default, you have to specify a new list of normalizations
 * to apply, via the {@link #setNormalizations(Normalization...)} method,
 * or via XML configuration.  Each
 * normalizations is identified by a code name.  The following is the
 * complete code name list for supported normalizations.  Click on any code
 * name to get a full description from {@link URLNormalizer}:
 * </p>
 * <ul>
 *   <li>{@link URLNormalizer#addDirectoryTrailingSlash() addDirectoryTrailingSlash} (since 2.6.0)</li>
 *   <li>{@link URLNormalizer#addDomainTrailingSlash() addDomainTrailingSlash} (since 2.6.1)</li>
 *   <li>{@link URLNormalizer#addWWW() addWWW}</li>
 *   <li>{@link URLNormalizer#decodeUnreservedCharacters() decodeUnreservedCharacters}</li>
 *   <li>{@link URLNormalizer#encodeNonURICharacters() encodeNonURICharacters}</li>
 *   <li>{@link URLNormalizer#encodeSpaces() encodeSpaces}</li>
 *   <li>{@link URLNormalizer#lowerCase() lowerCase} (since 2.9.0)</li>
 *   <li>{@link URLNormalizer#lowerCasePath() lowerCasePath} (since 2.9.0)</li>
 *   <li>{@link URLNormalizer#lowerCaseQuery() lowerCaseQuery} (since 2.9.0)</li>
 *   <li>{@link URLNormalizer#lowerCaseQueryParameterNames()
 *        lowerCaseQueryParameterNames} (since 2.9.0)</li>
 *   <li>{@link URLNormalizer#lowerCaseQueryParameterValues()
 *        lowerCaseQueryParameterValues} (since 2.9.0)</li>
 *   <li>{@link URLNormalizer#lowerCaseSchemeHost() lowerCaseSchemeHost}</li>
 *   <li>{@link URLNormalizer#removeDefaultPort() removeDefaultPort}</li>
 *   <li>{@link URLNormalizer#removeDirectoryIndex() removeDirectoryIndex}</li>
 *   <li>{@link URLNormalizer#removeDotSegments() removeDotSegments}</li>
 *   <li>{@link URLNormalizer#removeDuplicateSlashes() removeDuplicateSlashes}</li>
 *   <li>{@link URLNormalizer#removeEmptyParameters() removeEmptyParameters}</li>
 *   <li>{@link URLNormalizer#removeFragment() removeFragment}</li>
 *   <li>{@link URLNormalizer#removeQueryString() removeQueryString} (since 2.9.0)</li>
 *   <li>{@link URLNormalizer#removeSessionIds() removeSessionIds}</li>
 *   <li>{@link URLNormalizer#removeTrailingQuestionMark() removeTrailingQuestionMark}</li>
 *   <li>{@link URLNormalizer#removeTrailingSlash() removeTrailingSlash} (since 2.6.0)</li>
 *   <li>{@link URLNormalizer#removeTrailingHash() removeTrailingHash} (since 2.7.0)</li>
 *   <li>{@link URLNormalizer#removeWWW() removeWWW}</li>
 *   <li>{@link URLNormalizer#replaceIPWithDomainName() replaceIPWithDomainName}</li>
 *   <li>{@link URLNormalizer#secureScheme() secureScheme}</li>
 *   <li>{@link URLNormalizer#sortQueryParameters() sortQueryParameters}</li>
 *   <li>{@link URLNormalizer#unsecureScheme() unsecureScheme}</li>
 *   <li>{@link URLNormalizer#upperCaseEscapeSequence() upperCaseEscapeSequence}</li>
 * </ul>
 * <p>
 *   In addition, this class allows you to specify any number of URL
 *   value replacements using regular expressions.
 * </p>
 *
 * {@nx.xml.usage
 *  <urlNormalizer
 *      class="com.norconex.collector.http.url.impl.GenericURLNormalizer"
 *      disabled="[false|true]">
 *    <normalizations>
 *      (normalization code names, coma separated)
 *    </normalizations>
 *    <replacements>
 *      <replace>
 *         <match>(regex pattern to match)</match>
 *         <replacement>(optional replacement value, default to blank)</replacement>
 *      </replace>
 *      (... repeat replace tag  as needed ...)
 *    </replacements>
 *  </urlNormalizer>
 * }
 * <p>
 * Since 2.7.2, having an empty "normalizations" tag will effectively remove
 * any normalizations rules previously set (like default ones).
 * Not having the tag
 * at all will keep existing/default normalizations.
 * </p>
 *
 * {@nx.xml.example
 * <urlNormalizer class="com.norconex.collector.http.url.impl.GenericURLNormalizer">
 *   <normalizations>
 *       removeFragment, lowerCaseSchemeHost, upperCaseEscapeSequence,
 *       decodeUnreservedCharacters, removeDefaultPort,
 *       encodeNonURICharacters, addWWW
 *   </normalizations>
 *   <replacements>
 *     <replace><match>&amp;amp;view=print</match></replace>
 *     <replace>
 *        <match>(&amp;amp;type=)(summary)</match>
 *        <replacement>$1full</replacement>
 *     </replace>
 *   </replacements>
 * </urlNormalizer>
 * }
 * <p>
 * The following adds a normalization to add "www." to URL domains when
 * missing, to the default set of normalizations. It also add custom
 * URL "search-and-replace" to remove any "&amp;view=print" strings from URLs
 * as well as replace "&amp;type=summary" with "&amp;type=full".
 * </p>
 * @author Pascal Essiembre
 */
public class GenericURLNormalizer implements IURLNormalizer, IXMLConfigurable {

    private static final Logger LOG = LoggerFactory.getLogger(
            GenericURLNormalizer.class);

    //TODO Make upper case, with EnumConverter stripping nonAlphanum
    public enum Normalization {
        addDirectoryTrailingSlash,
        addDomainTrailingSlash,
        addWWW,
        decodeUnreservedCharacters,
        encodeNonURICharacters,
        encodeSpaces,
        lowerCase,
        lowerCasePath,
        lowerCaseQuery,
        lowerCaseQueryParameterNames,
        lowerCaseQueryParameterValues,
        lowerCaseSchemeHost,
        removeDefaultPort,
        removeDirectoryIndex,
        removeDotSegments,
        removeDuplicateSlashes,
        removeEmptyParameters,
        removeFragment,
        removeQueryString,
        removeSessionIds,
        removeTrailingQuestionMark,
        removeTrailingSlash,
        removeTrailingHash,
        removeWWW,
        replaceIPWithDomainName,
        secureScheme,
        sortQueryParameters,
        unsecureScheme,
        upperCaseEscapeSequence,
    }

    private final List<Normalization> normalizations = new ArrayList<>();
    private final List<Replace> replaces = new ArrayList<>();
    private boolean disabled;

    public GenericURLNormalizer() {
        super();
        setNormalizations(
                Normalization.removeFragment,
                Normalization.lowerCaseSchemeHost,
                Normalization.upperCaseEscapeSequence,
                Normalization.decodeUnreservedCharacters,
                Normalization.removeDefaultPort,
                Normalization.encodeNonURICharacters);
    }

    @Override
    public String normalizeURL(String url) {
        if (disabled) {
            return url;
        }

        URLNormalizer normalizer = new URLNormalizer(url);
        for (Normalization n : normalizations) {
            try {
                MethodUtils.invokeExactMethod(
                        normalizer, n.toString(), (Object[]) null);
            } catch (Exception e) {
                LOG.error("Could not apply normalization \"{}\".", n, e);
            }
        }
        String normedURL = normalizer.toString();
        for (Replace replace : replaces) {
            if (replace == null || StringUtils.isBlank(replace.getMatch())) {
                continue;
            }
            String replacement = replace.getReplacement();
            if (StringUtils.isBlank(replacement)) {
                replacement = StringUtils.EMPTY;
            }
            normedURL = normedURL.replaceAll(replace.getMatch(), replacement);
        }
        return normedURL;
    }

    public List<Normalization> getNormalizations() {
        return Collections.unmodifiableList(normalizations);
    }
    public void setNormalizations(Normalization... normalizations) {
        setNormalizations(Arrays.asList(normalizations));
    }
    public void setNormalizations(List<Normalization> normalizations) {
        CollectionUtil.setAll(this.normalizations, normalizations);
    }

    public List<Replace> getReplaces() {
        return Collections.unmodifiableList(replaces);
    }
    public void setReplaces(Replace... replaces) {
        setReplaces(Arrays.asList(replaces));
    }
    public void setReplaces(List<Replace> replaces) {
        CollectionUtil.setAll(this.replaces, replaces);
    }

    /**
     * Whether this URL Normalizer is disabled or not.
     * @return <code>true</code> if disabled
     * @since 2.3.0
     */
    public boolean isDisabled() {
        return disabled;
    }
    /**
     * Sets whether this URL Normalizer is disabled or not.
     * @param disabled <code>true</code> if disabled
     * @since 2.3.0
     */
    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public void loadFromXML(XML xml) {
        setDisabled(xml.getBoolean("@disabled", disabled));

        //TODO be consistant how to clear defaults... similar issue as with
        //GenericSitemapResolver
        List<String> norms = xml.getStringList("normalizations");
        if (norms.size() == 1 && norms.get(0).equals("")) {
            CollectionUtil.setAll(
                    this.normalizations, (List<Normalization>) null);
        } else if (!norms.isEmpty()) {
            CollectionUtil.setAll(this.normalizations, CollectionUtil.toTypeList(
                    xml.getDelimitedStringList("normalizations"),
                            s -> Normalization.valueOf(s.trim())));
        }

        List<XML> xmlReplaces = xml.getXMLList("replacements/replace");
        if (!xmlReplaces.isEmpty()) {
            replaces.clear();
        }
        for (XML xmlReplace : xmlReplaces) {
            String match = xmlReplace.getString("match", "");
            String replacement = xmlReplace.getString("replacement", "");
            replaces.add(new Replace(match, replacement));
        }
    }

    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("disabled", disabled);
        xml.addDelimitedElementList("normalizations", normalizations);
        if (!replaces.isEmpty()) {
            XML xmlReplaces = xml.addElement("replacements");
            for (Replace replace : replaces) {
                XML xmlReplace = xmlReplaces.addElement("replace");
                xmlReplace.addElement("match", replace.getMatch());
                xmlReplace.addElement("replacement", replace.getReplacement());
            }
        }
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

    public static class Replace {
        private final String match;
        private final String replacement;
        public Replace(String match) {
            super();
            this.match = match;
            this.replacement = "";
        }
        public Replace(String match, String replacement) {
            super();
            this.match = match;
            this.replacement = replacement;
        }
        public String getMatch() {
            return match;
        }
        public String getReplacement() {
            return replacement;
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
}
