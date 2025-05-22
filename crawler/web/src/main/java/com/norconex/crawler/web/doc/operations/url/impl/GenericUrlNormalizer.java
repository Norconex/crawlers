/* Copyright 2010-2025 Norconex Inc.
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

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.url.UrlNormalizer;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.doc.operations.url.WebUrlNormalizer;
import com.norconex.crawler.web.doc.operations.url.impl.GenericUrlNormalizerConfig.Normalization;
import com.norconex.crawler.web.doc.operations.url.impl.GenericUrlNormalizerConfig.NormalizationReplace;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Generic implementation of {@link WebUrlNormalizer} that should satisfy
 * most URL normalization needs.  This implementation relies on
 * {@link UrlNormalizer}.  Please refer to it for complete documentation and
 * examples.
 * </p>
 * <p>
 * This class is in effect by default. To skip its usage, you
 * can explicitly set the URL Normalizer to {@code null} in the
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
 *   <li>{@link UrlNormalizer#addDirectoryTrailingSlash() addDirectoryTrailingSlash} (since 2.6.0)</li>
 *   <li>{@link UrlNormalizer#addDomainTrailingSlash() addDomainTrailingSlash} (since 2.6.1)</li>
 *   <li>{@link UrlNormalizer#addWWW() addWWW}</li>
 *   <li>{@link UrlNormalizer#decodeUnreservedCharacters() decodeUnreservedCharacters}</li>
 *   <li>{@link UrlNormalizer#encodeNonURICharacters() encodeNonURICharacters}</li>
 *   <li>{@link UrlNormalizer#encodeSpaces() encodeSpaces}</li>
 *   <li>{@link UrlNormalizer#lowerCase() lowerCase} (since 2.9.0)</li>
 *   <li>{@link UrlNormalizer#lowerCasePath() lowerCasePath} (since 2.9.0)</li>
 *   <li>{@link UrlNormalizer#lowerCaseQuery() lowerCaseQuery} (since 2.9.0)</li>
 *   <li>{@link UrlNormalizer#lowerCaseQueryParameterNames()
 *        lowerCaseQueryParameterNames} (since 2.9.0)</li>
 *   <li>{@link UrlNormalizer#lowerCaseQueryParameterValues()
 *        lowerCaseQueryParameterValues} (since 2.9.0)</li>
 *   <li>{@link UrlNormalizer#lowerCaseSchemeHost() lowerCaseSchemeHost}</li>
 *   <li>{@link UrlNormalizer#removeDefaultPort() removeDefaultPort}</li>
 *   <li>{@link UrlNormalizer#removeDirectoryIndex() removeDirectoryIndex}</li>
 *   <li>{@link UrlNormalizer#removeDotSegments() removeDotSegments}</li>
 *   <li>{@link UrlNormalizer#removeDuplicateSlashes() removeDuplicateSlashes}</li>
 *   <li>{@link UrlNormalizer#removeEmptyParameters() removeEmptyParameters}</li>
 *   <li>{@link UrlNormalizer#removeFragment() removeFragment}</li>
 *   <li>{@link UrlNormalizer#removeQueryString() removeQueryString} (since 2.9.0)</li>
 *   <li>{@link UrlNormalizer#removeSessionIds() removeSessionIds}</li>
 *   <li>{@link UrlNormalizer#removeTrailingFragment() removeTrailingFragment} (since 3.1.0)</li>
 *   <li>{@link UrlNormalizer#removeTrailingQuestionMark() removeTrailingQuestionMark}</li>
 *   <li>{@link UrlNormalizer#removeTrailingSlash() removeTrailingSlash} (since 2.6.0)</li>
 *   <li>{@link UrlNormalizer#removeTrailingHash() removeTrailingHash} (since 2.7.0)</li>
 *   <li>{@link UrlNormalizer#removeWWW() removeWWW}</li>
 *   <li>{@link UrlNormalizer#replaceIPWithDomainName() replaceIPWithDomainName}</li>
 *   <li>{@link UrlNormalizer#secureScheme() secureScheme}</li>
 *   <li>{@link UrlNormalizer#sortQueryParameters() sortQueryParameters}</li>
 *   <li>{@link UrlNormalizer#unsecureScheme() unsecureScheme}</li>
 *   <li>{@link UrlNormalizer#upperCaseEscapeSequence() upperCaseEscapeSequence}</li>
 * </ul>
 * <p>
 *   In addition, this class allows you to specify any number of URL
 *   value replacements using regular expressions.
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
        var normalizer = new UrlNormalizer(url);
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
