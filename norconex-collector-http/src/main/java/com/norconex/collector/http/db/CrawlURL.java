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
package com.norconex.collector.http.db;

import java.io.Serializable;

import com.norconex.collector.http.crawler.CrawlStatus;

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
        final int prime = 31;
        int result = 1;
        result = prime * result + depth;
        result = prime * result
                + ((docChecksum == null) ? 0 : docChecksum.hashCode());
        result = prime * result
                + ((headChecksum == null) ? 0 : headChecksum.hashCode());
        result = prime * result + ((status == null) ? 0 : status.hashCode());
        result = prime * result + ((url == null) ? 0 : url.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CrawlURL other = (CrawlURL) obj;
        if (depth != other.depth)
            return false;
        if (docChecksum == null) {
            if (other.docChecksum != null)
                return false;
        } else if (!docChecksum.equals(other.docChecksum))
            return false;
        if (headChecksum == null) {
            if (other.headChecksum != null)
                return false;
        } else if (!headChecksum.equals(other.headChecksum))
            return false;
        if (status != other.status)
            return false;
        if (url == null) {
            if (other.url != null)
                return false;
        } else if (!url.equals(other.url))
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "CrawlerURL [depth=" + depth + ", url=" + url + ", status="
                + status + ", headChecksum=" + headChecksum + ", docChecksum="
                + docChecksum + "]";
    }
}
