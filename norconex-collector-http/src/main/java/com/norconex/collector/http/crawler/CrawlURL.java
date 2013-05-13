/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.crawler;

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


public class CrawlURL implements Serializable {

    private static final long serialVersionUID = -2219206220476107409L;
    private int depth;
    private String url;
    private CrawlStatus status;
    private String headChecksum;
    private String docChecksum;
    
    public CrawlURL() {
        super();
    }
    public int getDepth() {
        return depth;
    }
    public String getUrl() {
        return url;
    }
    public CrawlStatus getStatus() {
        return status;
    }
    public void setStatus(CrawlStatus status) {
        this.status = status;
    }
    public String getHeadChecksum() {
        return headChecksum;
    }
    public void setHeadChecksum(String headChecksum) {
        this.headChecksum = headChecksum;
    }
    public String getDocChecksum() {
        return docChecksum;
    }
    public void setDocChecksum(String docChecksum) {
        this.docChecksum = docChecksum;
    }
    public void setDepth(int depth) {
        this.depth = depth;
    }
    public void setUrl(String url) {
        this.url = url;
    }
    
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(depth)
            .append(docChecksum)
            .append(headChecksum)
            .append(status)
            .append(url)
            .toHashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof CrawlURL)) {
            return false;
        }
        CrawlURL other = (CrawlURL) obj;
        return new EqualsBuilder()
            .append(depth, other.depth)
            .append(docChecksum, other.docChecksum)
            .append(headChecksum, other.headChecksum)
            .append(status, other.status)
            .append(url, other.url)
            .isEquals();
    }
    
    @Override
    public String toString() {
        return "CrawlerURL [depth=" + depth + ", url=" + url + ", status="
                + status + ", headChecksum=" + headChecksum + ", docChecksum="
                + docChecksum + "]";
    }
}
