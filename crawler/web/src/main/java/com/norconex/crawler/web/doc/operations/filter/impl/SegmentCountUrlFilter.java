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
package com.norconex.crawler.web.doc.operations.filter.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.HttpURL;
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
 * Filters URL based based on the number of URL segments. A URL with
 * a number of segments equal or more than the specified count will either
 * be included or excluded, as specified.
 * </p>
 * <p>
 * By default
 * segments are obtained by breaking the URL text at each forward slashes
 * (/), starting after the host name.  You can define different or
 * additional segment separator characters.
 * </p>
 * <p>
 * When <code>duplicate</code> is <code>true</code>, it will count the maximum
 * number of duplicate segments found.
 * </p>
 * @since 1.2
 * @see Pattern
 */
@EqualsAndHashCode
@ToString
public class SegmentCountUrlFilter implements
        OnMatchFilter,
        ReferenceFilter,
        DocumentFilter,
        MetadataFilter,
        Configurable<SegmentCountUrlFilterConfig> {

    @Getter
    private final SegmentCountUrlFilterConfig configuration =
            new SegmentCountUrlFilterConfig();

    @Override
    public OnMatch getOnMatch() {
        return OnMatch.includeIfNull(configuration.getOnMatch());
    }

    @Override
    public boolean acceptDocument(Doc document) {
        return acceptReference(document.getReference());
    }

    @Override
    public boolean acceptMetadata(String reference, Properties metadata) {
        return acceptReference(reference);
    }

    @Override
    public boolean acceptReference(String url) {
        var isInclude = getOnMatch() == OnMatch.INCLUDE;
        if (configuration.getSeparator() == null) {
            return isInclude;
        }

        var cleanSegments = getCleanSegments(url);

        var reachedCount = false;
        if (configuration.isDuplicate()) {
            Map<String, Integer> segMap = new HashMap<>();
            for (String seg : cleanSegments) {
                var dupCount = segMap.get(seg);
                if (dupCount == null) {
                    dupCount = 0;
                }
                dupCount++;
                if (dupCount >= configuration.getCount()) {
                    reachedCount = true;
                    break;
                }
                segMap.put(seg, dupCount);
            }
        } else {
            reachedCount = cleanSegments.size() >= configuration.getCount();
        }

        return reachedCount == isInclude;
    }

    private List<String> getCleanSegments(String url) {
        var path = new HttpURL(url).getPath();
        var allSegments = Pattern.compile(
                configuration.getSeparator()).split(path);
        // remove empty/nulls
        List<String> cleanSegments = new ArrayList<>();
        for (String segment : allSegments) {
            if (StringUtils.isNotBlank(segment)) {
                cleanSegments.add(segment);
            }
        }
        return cleanSegments;
    }
}
