/* Copyright 2014 Norconex Inc.
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
package com.norconex.collector.http.url;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Represents a link extracted from a document.  A link is typically a URL,
 * with optional data associated with it, such as the referrer, text and
 * markup tag used to hold the URL.
 * @author Pascal Essiembre
 */
public class Link implements Comparable<Link> {
    
    private final String url;
    private String text;
    private String referrer;
    private String tag;
    private String title;
    
    public Link(String url) {
        super();
        this.url = url;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getUrl() {
        return url;
    }

    public String getReferrer() {
        return referrer;
    }
    public void setReferrer(String referrer) {
        this.referrer = referrer;
    }
    
    public String getTag() {
        return tag;
    }
    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof Link))
            return false;
        Link castOther = (Link) other;
        return new EqualsBuilder()
                .append(url, castOther.url)
                .append(tag, castOther.tag)
                .append(text, castOther.text)
                .append(title, castOther.title)
                .append(referrer, castOther.referrer)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(url)
                .append(tag)
                .append(text)
                .append(title)
                .append(referrer)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("url", url)
                .append("tag", tag)
                .append("text", text)
                .append("title", title)
                .append("referrer", referrer)
                .toString();
    }

    public int compareTo(final Link other) {
        return new CompareToBuilder().append(url, other.url).toComparison();
    }
}
