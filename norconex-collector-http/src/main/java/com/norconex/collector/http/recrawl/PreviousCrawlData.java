/* Copyright 2010-2020 Norconex Inc.
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
package com.norconex.collector.http.recrawl;

import java.time.ZonedDateTime;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.file.ContentType;

/**
 * Previously crawled data.
 * @author Pascal Essiembre
 * @since 2.5.0
 */
@Deprecated
public class PreviousCrawlData {

    // Deprecating until we find out if still needed.
//TODO keep now that we are carrying cached doc info around??

    private String reference;
    private ContentType contentType;
    private ZonedDateTime crawlDate;
    private ZonedDateTime sitemapLastMod;
    private String sitemapChangeFreq;
    private Float sitemapPriority;

    public String getReference() {
        return reference;
    }
    public void setReference(String reference) {
        this.reference = reference;
    }
    public ContentType getContentType() {
        return contentType;
    }
    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }
    public ZonedDateTime getCrawlDate() {
        return crawlDate;
    }
    public void setCrawlDate(ZonedDateTime crawlDate) {
        this.crawlDate = crawlDate;
    }
    public ZonedDateTime getSitemapLastMod() {
        return sitemapLastMod;
    }
    public void setSitemapLastMod(ZonedDateTime sitemapLastMod) {
        this.sitemapLastMod = sitemapLastMod;
    }
    public String getSitemapChangeFreq() {
        return sitemapChangeFreq;
    }
    public void setSitemapChangeFreq(String sitemapChangeFreq) {
        this.sitemapChangeFreq = sitemapChangeFreq;
    }
    public Float getSitemapPriority() {
        return sitemapPriority;
    }
    public void setSitemapPriority(Float sitemapPriority) {
        this.sitemapPriority = sitemapPriority;
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
