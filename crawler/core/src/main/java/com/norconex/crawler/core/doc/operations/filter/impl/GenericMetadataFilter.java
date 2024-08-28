/* Copyright 2021-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations.filter.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.crawler.core.doc.operations.filter.DocumentFilter;
import com.norconex.crawler.core.doc.operations.filter.MetadataFilter;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.crawler.core.doc.operations.filter.OnMatchFilter;
import com.norconex.importer.doc.Doc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Accepts or rejects a reference based on whether one or more
 * metadata field values are matching.
 * </p>
 *
 * {@nx.xml.usage
 * <filter class="com.norconex.crawler.core.filter.impl.GenericMetadataFilter"
 *     onMatch="[include|exclude]">
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (Expression matching one or more fields to evaluate.)
 *   </fieldMatcher>
 *   <valueMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (Expression matching one or more values from matching fields.)
 *   </valueMatcher>
 * </filter>
 * }
 *
 * {@nx.xml.example
 * <filter class="GenericMetadataFilter" onMatch="exclude">
 *   <fieldMatcher>Content-Type</fieldMatcher>
 *   <valueMatcher>application/zip</valueMatcher>
 * </filter>
 * }
 * <p>
 * Used in a web context, the above example filters out Zip documents base
 * on a "Content-Type" metadata field.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class GenericMetadataFilter implements
        OnMatchFilter,
        MetadataFilter,
        DocumentFilter,
        Configurable<GenericMetadataFilterConfig> {

    @Getter
    private final GenericMetadataFilterConfig configuration =
            new GenericMetadataFilterConfig();

    @Override
    public OnMatch getOnMatch() {
        return OnMatch.includeIfNull(configuration.getOnMatch());
    }

    @Override
    public boolean acceptMetadata(String reference, Properties metadata) {
        if (isBlank(configuration.getFieldMatcher().getPattern())
                || isBlank(configuration.getValueMatcher().getPattern())
                || new PropertyMatcher(
                        configuration.getFieldMatcher(),
                        configuration.getValueMatcher()
                ).matches(metadata)) {
            return getOnMatch() == OnMatch.INCLUDE;
        }
        return getOnMatch() == OnMatch.EXCLUDE;
    }

    @Override
    public boolean acceptDocument(Doc document) {
        if (document == null) {
            return getOnMatch() == OnMatch.INCLUDE;
        }
        return acceptMetadata(document.getReference(), document.getMetadata());
    }
}
