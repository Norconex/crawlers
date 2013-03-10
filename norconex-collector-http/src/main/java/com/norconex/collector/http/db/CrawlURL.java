package com.norconex.collector.http.db;

import java.io.Serializable;

import com.norconex.collector.http.crawler.CrawlStatus;

public class CrawlURL implements Serializable {

    private static final long serialVersionUID = -2219206220476107409L;
    private final int depth;
    private final String url;
    private CrawlStatus status;
    private String headChecksum;
    private String docChecksum;
    
    public CrawlURL(String url, int depth) {
        super();
        this.depth = depth;
        this.url = url;
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
