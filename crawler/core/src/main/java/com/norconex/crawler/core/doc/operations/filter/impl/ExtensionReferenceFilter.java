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
package com.norconex.crawler.core.doc.operations.filter.impl;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.io.FilenameUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.doc.operations.filter.DocumentFilter;
import com.norconex.crawler.core.doc.operations.filter.MetadataFilter;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.crawler.core.doc.operations.filter.OnMatchFilter;
import com.norconex.crawler.core.doc.operations.filter.ReferenceFilter;
import com.norconex.importer.doc.Doc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Filters a reference based on a comma-separated list of extensions.
 * Extensions are typically the last characters of a file name, after the
 * last dot.
 * </p>
 */
@EqualsAndHashCode
@ToString
public class ExtensionReferenceFilter implements
        OnMatchFilter,
        ReferenceFilter,
        DocumentFilter,
        MetadataFilter,
        Configurable<ExtensionReferenceFilterConfig> {

    @Getter
    private final ExtensionReferenceFilterConfig configuration =
            new ExtensionReferenceFilterConfig();

    @Override
    public OnMatch getOnMatch() {
        return OnMatch.includeIfNull(configuration.getOnMatch());
    }

    @Override
    public boolean acceptReference(String reference) {
        var safeOnMatch = getOnMatch();

        if (configuration.getExtensions().isEmpty()) {
            return safeOnMatch == OnMatch.INCLUDE;
        }
        String referencePath;
        try {
            var referenceUrl = new URL(reference);
            referencePath = referenceUrl.getPath();
        } catch (MalformedURLException ex) {
            referencePath = reference;
        }

        var refExtension = FilenameUtils.getExtension(referencePath);

        for (String ext : configuration.getExtensions()) {
            if ((configuration.isIgnoreCase()
                    && ext.equalsIgnoreCase(refExtension))
                    || (!configuration.isIgnoreCase()
                            && ext.equals(refExtension))) {
                return safeOnMatch == OnMatch.INCLUDE;
            }
        }
        return safeOnMatch == OnMatch.EXCLUDE;
    }

    @JsonIgnore
    @Override
    public boolean acceptDocument(Doc document) {
        return acceptReference(document.getReference());
    }

    @JsonIgnore
    @Override
    public boolean acceptMetadata(String reference, Properties metadata) {
        return acceptReference(reference);
    }
}
