/* Copyright 2010-2024 Norconex Inc.
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
import com.norconex.commons.lang.url.UrlNormalizer;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link GenericUrlNormalizer}.
 * </p>
 */
@Data
@Accessors(chain = true)
public class GenericUrlNormalizerConfig {

    public enum Normalization {
        ADD_DIRECTORY_TRAILING_SLASH(UrlNormalizer::addDirectoryTrailingSlash),
        ADD_DOMAIN_TRAILING_SLASH(UrlNormalizer::addDomainTrailingSlash),
        ADD_WWW(UrlNormalizer::addWWW),
        DECODE_UNRESERVED_CHARACTERS(UrlNormalizer::decodeUnreservedCharacters),
        ENCODE_NON_URI_CHARACTERS(UrlNormalizer::encodeNonURICharacters),
        ENCODE_SPACES(UrlNormalizer::encodeSpaces),
        LOWERCASE(UrlNormalizer::lowerCase),
        LOWERCASE_PATH(UrlNormalizer::lowerCasePath),
        LOWERCASE_QUERY(UrlNormalizer::lowerCaseQuery),
        LOWERCASE_QUERY_PARAMETER_NAMES(
                UrlNormalizer::lowerCaseQueryParameterNames),
        LOWERCASE_QUERY_PARAMETER_VALUES(
                UrlNormalizer::lowerCaseQueryParameterValues),
        LOWERCASE_SCHEME_HOST(UrlNormalizer::lowerCaseSchemeHost),
        REMOVE_DEFAULT_PORT(UrlNormalizer::removeDefaultPort),
        REMOVE_DIRECTORY_INDEX(UrlNormalizer::removeDirectoryIndex),
        REMOVE_DOT_SEGMENTS(UrlNormalizer::removeDotSegments),
        REMOVE_DUPLICATE_SLASHES(UrlNormalizer::removeDuplicateSlashes),
        REMOVE_EMPTY_PARAMETERS(UrlNormalizer::removeEmptyParameters),
        REMOVE_FRAGMENT(UrlNormalizer::removeFragment),
        REMOVE_QUERY_STRING(UrlNormalizer::removeQueryString),
        REMOVE_SESSION_IDS(UrlNormalizer::removeSessionIds),
        REMOVE_TRAILING_FRAGMENT(UrlNormalizer::removeTrailingFragment),
        REMOVE_TRAILING_QUESTION_MARK(
                UrlNormalizer::removeTrailingQuestionMark),
        REMOVE_TRAILING_SLASH(UrlNormalizer::removeTrailingSlash),
        REMOVE_TRAILING_HASH(UrlNormalizer::removeTrailingHash),
        REMOVE_WWW(UrlNormalizer::removeWWW),
        REPLACE_IP_WITH_DOMAIN_NAME(UrlNormalizer::replaceIPWithDomainName),
        SECURE_SCHEME(UrlNormalizer::secureScheme),
        SORT_QUERY_PARAMETERS(UrlNormalizer::sortQueryParameters),
        UNSECURE_SCHEME(UrlNormalizer::unsecureScheme),
        UPPERCASE_ESCAPESEQUENCE(UrlNormalizer::upperCaseEscapeSequence),
        ;

        @Getter
        private final Consumer<UrlNormalizer> consumer;

        Normalization(Consumer<UrlNormalizer> c) {
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
        setNormalizations(
                List.of(
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

    public GenericUrlNormalizerConfig setReplacements(
            List<NormalizationReplace> replacements) {
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
                @JsonProperty("match") String match,
                @JsonProperty("value") String replacement) {
            this.match = match;
            value = replacement;
        }
    }
}
