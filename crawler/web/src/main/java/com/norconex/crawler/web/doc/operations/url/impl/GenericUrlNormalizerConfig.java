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
package com.norconex.crawler.web.doc.operations.url.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.convert.GenericConverter;
import com.norconex.commons.lang.url.URLNormalizer;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.doc.operations.url.WebUrlNormalizer;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * <p>
 * Generic implementation of {@link WebUrlNormalizer} that should satisfy
 * most URL normalization needs.  This implementation relies on
 * {@link URLNormalizer}.  Please refer to it for complete documentation and
 * examples.
 * </p>
 * <p>
 * This class is in effect by default. To skip its usage, you
 * can explicitly set the URL Normalizer to <code>null</code> in the
 * {@link WebCrawlerConfig}.
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
 * to apply, via the {@link #setNormalizations(List)} method,
 * or via XML configuration.  Each
 * normalizations is identified by a code name.  The following is the
 * complete code name list for supported normalizations.  Click on any code
 * name to get a full description from {@link WebUrlNormalizer}:
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
 *      class="com.norconex.crawler.web.url.impl.GenericUrlNormalizer">
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
 * <urlNormalizer class="com.norconex.crawler.web.url.impl.GenericUrlNormalizer">
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
 */
@Data
@Accessors(chain = true)
public class GenericUrlNormalizerConfig {

    public enum Normalization {
        ADD_DIRECTORY_TRAILING_SLASH(URLNormalizer::addDirectoryTrailingSlash),
        ADD_DOMAIN_TRAILING_SLASH(URLNormalizer::addDomainTrailingSlash),
        ADD_WWW(URLNormalizer::addWWW),
        DECODE_UNRESERVED_CHARACTERS(URLNormalizer::decodeUnreservedCharacters),
        ENCODE_NON_URI_CHARACTERS(URLNormalizer::encodeNonURICharacters),
        ENCODE_SPACES(URLNormalizer::encodeSpaces),
        LOWERCASE(URLNormalizer::lowerCase),
        LOWERCASE_PATH(URLNormalizer::lowerCasePath),
        LOWERCASE_QUERY(URLNormalizer::lowerCaseQuery),
        LOWERCASE_QUERY_PARAMETER_NAMES(
                URLNormalizer::lowerCaseQueryParameterNames),
        LOWERCASE_QUERY_PARAMETER_VALUES(
                URLNormalizer::lowerCaseQueryParameterValues),
        LOWERCASE_SCHEME_HOST(URLNormalizer::lowerCaseSchemeHost),
        REMOVE_DEFAULT_PORT(URLNormalizer::removeDefaultPort),
        REMOVE_DIRECTORY_INDEX(URLNormalizer::removeDirectoryIndex),
        REMOVE_DOT_SEGMENTS(URLNormalizer::removeDotSegments),
        REMOVE_DUPLICATE_SLASHES(URLNormalizer::removeDuplicateSlashes),
        REMOVE_EMPTY_PARAMETERS(URLNormalizer::removeEmptyParameters),
        REMOVE_FRAGMENT(URLNormalizer::removeFragment),
        REMOVE_QUERY_STRING(URLNormalizer::removeQueryString),
        REMOVE_SESSION_IDS(URLNormalizer::removeSessionIds),
        REMOVE_TRAILING_QUESTION_MARK(
                URLNormalizer::removeTrailingQuestionMark),
        REMOVE_TRAILING_SLASH(URLNormalizer::removeTrailingSlash),
        REMOVE_TRAILING_HASH(URLNormalizer::removeTrailingHash),
        REMOVE_WWW(URLNormalizer::removeWWW),
        REPLACE_IP_WITH_DOMAIN_NAME(URLNormalizer::replaceIPWithDomainName),
        SECURE_SCHEME(URLNormalizer::secureScheme),
        SORT_QUERY_PARAMETERS(URLNormalizer::sortQueryParameters),
        UNSECURE_SCHEME(URLNormalizer::unsecureScheme),
        UPPERCASE_ESCAPESEQUENCE(URLNormalizer::upperCaseEscapeSequence),
        ;
        @Getter
        private final Consumer<URLNormalizer> consumer;
        Normalization(Consumer<URLNormalizer> c) {
            consumer = c;
        }

        @JsonCreator
        static Normalization of(String name) {
            return GenericConverter.convert(name, Normalization.class);
        }
    }

    private final List<Normalization> normalizations = new ArrayList<>();
    private final List<NormalizationReplace> replacements = new ArrayList<>();

    public GenericUrlNormalizerConfig() {
        setNormalizations(List.of(
                Normalization.REMOVE_FRAGMENT,
                Normalization.LOWERCASE_SCHEME_HOST,
                Normalization.UPPERCASE_ESCAPESEQUENCE,
                Normalization.DECODE_UNRESERVED_CHARACTERS,
                Normalization.REMOVE_DEFAULT_PORT,
                Normalization.ENCODE_NON_URI_CHARACTERS));
    }

    public List<Normalization> getNormalizations() {
        return Collections.unmodifiableList(normalizations);
    }
    public GenericUrlNormalizerConfig setNormalizations(
            List<Normalization> normalizations) {
        CollectionUtil.setAll(this.normalizations, normalizations);
        return this;
    }

    public List<NormalizationReplace> getReplacements() {
        return Collections.unmodifiableList(replacements);
    }
    public GenericUrlNormalizerConfig setReplacements(List<NormalizationReplace> replacements) {
        CollectionUtil.setAll(this.replacements, replacements);
        return this;
    }

    @Data
    public static class NormalizationReplace {
        private final String match;
        private final String value;
        public NormalizationReplace(String match) {
            this.match = match;
            value = null;
        }
        @JsonCreator
        public NormalizationReplace(
                @JsonProperty("match")
                String match,
                @JsonProperty("value")
                String replacement) {
            this.match = match;
            value = replacement;
        }
    }
}
