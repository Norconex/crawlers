/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.crawler.core.filter.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.filter.DocumentFilter;
import com.norconex.crawler.core.filter.MetadataFilter;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.handler.filter.OnMatchFilter;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Filters a reference based on a comma-separated list of extensions.
 * Extensions are typically the last characters of a file name, after the
 * last dot.
 * </p>
 *
 * {@nx.xml.usage
 * <filter class="com.norconex.crawler.core.filter.impl.ExtensionReferenceFilter"
 *     onMatch="[include|exclude]"
 *     ignoreCase="[false|true]" >
 *   (comma-separated list of extensions)
 * </filter>
 * }
 *
 * {@nx.xml.example
 * <filter class="com.norconex.crawler.core.filter.impl.ExtensionReferenceFilter">
 *   html,htm,php,asp
 * </filter>
 * }
 * <p>
 * The above example will only accept references with the following
 * extensions: .html, .htm, .php, and .asp.
 * </p>
 */
@EqualsAndHashCode
@ToString
public class ExtensionReferenceFilter implements
        OnMatchFilter,
        ReferenceFilter,
        DocumentFilter,
        MetadataFilter,
        XMLConfigurable {

    private boolean ignoreCase;
    private final Set<String> extensions = new HashSet<>();
    private OnMatch onMatch;

    public ExtensionReferenceFilter() {
        this(null, OnMatch.INCLUDE, false);
    }
    public ExtensionReferenceFilter(String extensions) {
        this(extensions, OnMatch.INCLUDE, false);
    }
    public ExtensionReferenceFilter(String extensions, OnMatch onMatch) {
        this(extensions, onMatch, false);
    }
    public ExtensionReferenceFilter(
            String extensions, OnMatch onMatch, boolean ignoreCase) {
        setExtensions(extensions);
        setOnMatch(onMatch);
        setIgnoreCase(ignoreCase);
    }

    @Override
    public OnMatch getOnMatch() {
        return onMatch;
    }
    public void setOnMatch(OnMatch onMatch) {
        this.onMatch = onMatch;
    }

    @Override
    public boolean acceptReference(String reference) {
        var safeOnMatch = OnMatch.includeIfNull(onMatch);

        if (extensions.isEmpty()) {
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

        for (String ext : extensions) {
            if ((isIgnoreCase() && ext.equalsIgnoreCase(refExtension))
                    || (!isIgnoreCase() && ext.equals(refExtension))) {
                return safeOnMatch == OnMatch.INCLUDE;
            }
        }
        return safeOnMatch == OnMatch.EXCLUDE;
    }

    public Set<String> getExtensions() {
        return Collections.unmodifiableSet(extensions);
    }
    @JsonIgnore
    public void setExtensions(String... extensions) {
        CollectionUtil.setAll(this.extensions, extensions);
    }
    public void setExtensions(List<String> extensions) {
        CollectionUtil.setAll(this.extensions, extensions);
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }
    public final void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    @JsonIgnore
    @Override
    public void loadFromXML(XML xml)  {
        setExtensions(xml.getDelimitedStringList("."));
        setOnMatch(xml.getEnum("@onMatch", OnMatch.class, onMatch));
        setIgnoreCase(xml.getBoolean("@ignoreCase", ignoreCase));
    }
    @JsonIgnore
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("onMatch", onMatch);
        xml.setAttribute("ignoreCase", ignoreCase);
        xml.setTextContent(StringUtils.join(extensions, ','));
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
