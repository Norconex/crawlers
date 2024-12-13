/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.operations.filter.impl;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.cmd.crawl.operations.filter.DocumentFilter;
import com.norconex.crawler.core.cmd.crawl.operations.filter.MetadataFilter;
import com.norconex.crawler.core.cmd.crawl.operations.filter.OnMatch;
import com.norconex.crawler.core.cmd.crawl.operations.filter.OnMatchFilter;
import com.norconex.crawler.core.cmd.crawl.operations.filter.ReferenceFilter;
import com.norconex.importer.doc.Doc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Filters URL based on a matching expression.
 * </p>
 * @see Pattern
 */
@EqualsAndHashCode
@ToString
public class GenericReferenceFilter implements
        OnMatchFilter,
        ReferenceFilter,
        DocumentFilter,
        MetadataFilter,
        Configurable<GenericReferenceFilterConfig> {

    @Getter
    private final GenericReferenceFilterConfig configuration =
            new GenericReferenceFilterConfig();

    @Override
    public OnMatch getOnMatch() {
        return OnMatch.includeIfNull(configuration.getOnMatch());
    }

    @Override
    public boolean acceptReference(String reference) {
        var isInclude = getOnMatch() == OnMatch.INCLUDE;
        if (StringUtils.isBlank(configuration.getValueMatcher().getPattern())) {
            return isInclude;
        }
        var matches = configuration.getValueMatcher().matches(reference);
        return matches == isInclude;
    }

    @Override
    public boolean acceptDocument(Doc document) {
        return acceptReference(document.getReference());
    }

    @Override
    public boolean acceptMetadata(String reference, Properties metadata) {
        return acceptReference(reference);
    }
}
