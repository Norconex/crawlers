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
package com.norconex.collector.http.ref;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.norconex.collector.core.ref.BasicReference;


/**
 * A URL being crawled holding relevant crawl information.
 * @author Pascal Essiembre
 */
public class HttpDocReference extends BasicReference {

    private static final long serialVersionUID = -2219206220476107409L;

    private int depth;
    private String urlRoot;
    private Long sitemapLastMod;
    private String sitemapChangeFreq;
    private Float sitemapPriority;

    public HttpDocReference() {
        super();
    }

    /**
     * Constructor.
     * @param url URL being crawled
     * @param depth URL depth
     */
    public HttpDocReference(String url, int depth) {
        super();
        setReference(url);
        setDepth(depth);
    }

    /**
     * Gets the URL depth.
     * @return URL depth
     */
    public int getDepth() {
        return depth;
    }
    /**
     * Gets the sitemap last modified date in milliseconds (EPOCH date).
     * @return date as long
     */
    public Long getSitemapLastMod() {
        return sitemapLastMod;
    }
    /**
     * Sets the sitemap last modified date in milliseconds (EPOCH date).
     * @param sitemapLastMod date as long
     */
    public void setSitemapLastMod(Long sitemapLastMod) {
        this.sitemapLastMod = sitemapLastMod;
    }
    
    /**
     * Gets the sitemap change frequency.
     * @return sitemap change frequency
     */
    public String getSitemapChangeFreq() {
        return sitemapChangeFreq;
    }
    /**
     * Sets the sitemap change frequency.
     * @param sitemapChangeFreq sitemap change frequency
     */
    public void setSitemapChangeFreq(String sitemapChangeFreq) {
        this.sitemapChangeFreq = sitemapChangeFreq;
    }
    
    /**
     * Gets the sitemap priority.
     * @return sitemap priority
     */
    public Float getSitemapPriority() {
        return sitemapPriority;
    }
    /**
     * Sets the sitemap priority.
     * @param sitemapPriority sitemap priority
     */
    public void setSitemapPriority(Float sitemapPriority) {
        this.sitemapPriority = sitemapPriority;
    }

    /**
     * Sets the URL depth.
     * @param depth URL depth
     */
    public final void setDepth(int depth) {
        this.depth = depth;
    }
    @Override
    public final void setReference(String url) {
        super.setReference(url);
        if (url != null) {
            this.urlRoot = url.replaceFirst("(.*?://.*?)(/.*)", "$1");
        } else {
            this.urlRoot = null;
        }
    }
    /**
     * Gets the URL root (protocol + domain, e.g. http://www.host.com).
     * @return URL root
     */
    public String getUrlRoot() {
        return urlRoot;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + depth;
        result = prime
                * result
                + ((sitemapChangeFreq == null) ? 0 : sitemapChangeFreq
                        .hashCode());
        result = prime * result
                + ((sitemapLastMod == null) ? 0 : sitemapLastMod.hashCode());
        result = prime * result
                + ((sitemapPriority == null) ? 0 : sitemapPriority.hashCode());
        result = prime * result + ((urlRoot == null) ? 0 : urlRoot.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof HttpDocReference)) {
            return false;
        }
        HttpDocReference other = (HttpDocReference) obj;
        if (depth != other.depth) {
            return false;
        }
        if (sitemapChangeFreq == null) {
            if (other.sitemapChangeFreq != null) {
                return false;
            }
        } else if (!sitemapChangeFreq.equals(other.sitemapChangeFreq)) {
            return false;
        }
        if (sitemapLastMod == null) {
            if (other.sitemapLastMod != null) {
                return false;
            }
        } else if (!sitemapLastMod.equals(other.sitemapLastMod)) {
            return false;
        }
        if (sitemapPriority == null) {
            if (other.sitemapPriority != null) {
                return false;
            }
        } else if (!sitemapPriority.equals(other.sitemapPriority)) {
            return false;
        }
        if (urlRoot == null) {
            if (other.urlRoot != null) {
                return false;
            }
        } else if (!urlRoot.equals(other.urlRoot)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.appendSuper(super.toString());
        builder.append("depth", depth);
        builder.append("urlRoot", urlRoot);
        builder.append("sitemapLastMod", sitemapLastMod);
        builder.append("sitemapChangeFreq", sitemapChangeFreq);
        builder.append("sitemapPriority", sitemapPriority);
        return builder.toString();
    }


    

    
}
