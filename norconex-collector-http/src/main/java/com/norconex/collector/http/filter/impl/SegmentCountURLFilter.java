/* Copyright 2010-2018 Norconex Inc.
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
package com.norconex.collector.http.filter.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.collector.core.filter.IDocumentFilter;
import com.norconex.collector.core.filter.IMetadataFilter;
import com.norconex.collector.core.filter.IReferenceFilter;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.ImporterDocument;
import com.norconex.importer.handler.filter.IOnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;
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
 *
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;filter class="com.norconex.collector.http.filter.impl.SegmentCountURLFilter"
 *          onMatch="[include|exclude]"
 *          count="(numeric value)"
 *          duplicate="[false|true]"
 *          separator="(a regex identifying segment separator)" /&gt;
 * </pre>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following will reject URLs with more than 5 forward slashes after
 * the domain.
 * </p>
 * <pre>
 *  &lt;filter class="com.norconex.collector.http.filter.impl.SegmentCountURLFilter"
 *          onMatch="exclude" count="5" /&gt;
 * </pre>
 *
 * @author Pascal Essiembre
 * @since 1.2
 * @see Pattern
 */
public class SegmentCountURLFilter implements IOnMatchFilter,
        IReferenceFilter, IDocumentFilter, IMetadataFilter, IXMLConfigurable{

    /** Default segment separator pattern. */
    public static final String DEFAULT_SEGMENT_SEPARATOR_PATTERN = "/";
    /** Default segment count. */
    public static final int DEFAULT_SEGMENT_COUNT = 10;

    private String separator = DEFAULT_SEGMENT_SEPARATOR_PATTERN;
    private int count = DEFAULT_SEGMENT_COUNT;
    private boolean duplicate;
    private Pattern separatorPattern;

    private OnMatch onMatch;

    /**
     * Constructor.
     */
    public SegmentCountURLFilter() {
        this(DEFAULT_SEGMENT_COUNT);
    }
    /**
     * Constructor.
     * @param count how many segment
     */
    public SegmentCountURLFilter(int count) {
        this(count, OnMatch.INCLUDE);
    }
    /**
     * Constructor.
     * @param count how many segment
     * @param onMatch what to do on match
     */
    public SegmentCountURLFilter(
            int count, OnMatch onMatch) {
        this(count, onMatch, false);
    }
    /**
     * Constructor.
     * @param count how many segment
     * @param onMatch what to do on match
     * @param duplicate whether to handle duplicates
     */
    public SegmentCountURLFilter(
            int count, OnMatch onMatch, boolean duplicate) {
        super();
        setCount(count);
        setOnMatch(onMatch);
        setDuplicate(duplicate);
        setSeparator(DEFAULT_SEGMENT_SEPARATOR_PATTERN);
    }

    /**
     * Gets the segment separator pattern
     * @return the pattern
     */
    public String getSeparator() {
        return separator;
    }
    public final void setSeparator(String separator) {
        this.separator = separator;
        if (StringUtils.isNotBlank(separator)) {
            this.separatorPattern = Pattern.compile(separator);
        } else {
            this.separatorPattern = Pattern.compile(
                    DEFAULT_SEGMENT_SEPARATOR_PATTERN);
        }
    }

    public int getCount() {
        return count;
    }
    public final void setCount(int count) {
        this.count = count;
    }

    public boolean isDuplicate() {
        return duplicate;
    }
    public final void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    @Override
    public OnMatch getOnMatch() {
        return onMatch;
    }
    public void setOnMatch(OnMatch onMatch) {
        this.onMatch = onMatch;
    }

    @Override
    public boolean acceptDocument(ImporterDocument document) {
        return acceptReference(document.getReference());
    }
    @Override
    public boolean acceptMetadata(String reference, Properties metadata) {
        return acceptReference(reference);
    }
    @Override
    public boolean acceptReference(String url) {
        boolean isInclude = getOnMatch() == OnMatch.INCLUDE;
        if (StringUtils.isBlank(separator)) {
            return isInclude;
        }

        List<String> cleanSegments = getCleanSegments(url);

        boolean reachedCount = false;
        if (duplicate) {
            Map<String, Integer> segMap = new HashMap<>();
            for (String seg : cleanSegments) {
                Integer dupCount = segMap.get(seg);
                if (dupCount == null) {
                    dupCount = 0;
                }
                dupCount++;
                if (dupCount >= count) {
                    reachedCount = true;
                    break;
                }
                segMap.put(seg, dupCount);
            }
        } else {
            reachedCount = cleanSegments.size() >= count;
        }

        return reachedCount && isInclude || !reachedCount && !isInclude;
    }

    private List<String> getCleanSegments(String url) {
        String path = new HttpURL(url).getPath();
        String[] allSegments = separatorPattern.split(path);
        // remove empty/nulls
        List<String> cleanSegments = new ArrayList<>();
        for (String segment : allSegments) {
            if (StringUtils.isNotBlank(segment)) {
                cleanSegments.add(segment);
            }
        }
        return cleanSegments;
    }

    @Override
    public void loadFromXML(XML xml) {
        setSeparator(xml.getString("."));
        setCount(xml.getInteger("@count", DEFAULT_SEGMENT_COUNT));
        setDuplicate(xml.getBoolean("@duplicate", false));
        setSeparator(xml.getString(
                "@separator", DEFAULT_SEGMENT_SEPARATOR_PATTERN));
        setOnMatch(xml.getEnum("@onMatch", OnMatch.class, onMatch));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("count", count);
        xml.setAttribute("duplicate", duplicate);
        xml.setAttribute("separator", separator);
        xml.setAttribute("onMatch", onMatch);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other, "separatorPattern");
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, "separatorPattern");
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE)
                        .setExcludeFieldNames("separatorPattern").toString();
    }
}
