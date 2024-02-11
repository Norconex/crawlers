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
package com.norconex.crawler.web.url.impl;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.url.URLNormalizer;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import com.norconex.crawler.web.url.WebUrlNormalizer;
import com.norconex.crawler.web.url.impl.GenericUrlNormalizerConfig.Normalization;
import com.norconex.crawler.web.url.impl.GenericUrlNormalizerConfig.NormalizationReplace;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

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
 * to apply, via the {@link GenericUrlNormalizerConfig#setNormalizations(List)}
 * method, or via XML configuration.  Each
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
@EqualsAndHashCode
@ToString
public class GenericUrlNormalizer implements
        WebUrlNormalizer, Configurable<GenericUrlNormalizerConfig> {

    @Getter
    private final GenericUrlNormalizerConfig configuration =
            new GenericUrlNormalizerConfig();

    @Override
    public String normalizeURL(String url) {
        var normalizer = new URLNormalizer(url);
        for (Normalization n : configuration.getNormalizations()) {
            n.getConsumer().accept(normalizer);
        }
        var normedURL = normalizer.toString();
        for (NormalizationReplace replace : configuration.getReplacements()) {
            if (replace == null || StringUtils.isBlank(replace.getMatch())) {
                continue;
            }
            var replacement = replace.getValue();
            if (StringUtils.isBlank(replacement)) {
                replacement = StringUtils.EMPTY;
            }
            normedURL = normedURL.replaceAll(replace.getMatch(), replacement);
        }
        return normedURL;
    }
}
