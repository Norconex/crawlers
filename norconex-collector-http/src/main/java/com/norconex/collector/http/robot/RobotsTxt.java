package com.norconex.collector.http.robot;

import java.io.Serializable;

import com.norconex.collector.http.filter.IURLFilter;

public class RobotsTxt implements Serializable {

    private static final long serialVersionUID = -2203572498193869416L;
    
    public static final float UNSPECIFIED_CRAWL_DELAY = -1;
    
    private final IURLFilter[] filters;
    private final float crawlDelay ;
    
    public RobotsTxt(IURLFilter[] filters) {
        this(filters, UNSPECIFIED_CRAWL_DELAY);
    }
    public RobotsTxt(IURLFilter[] filters, float crawlDelay) {
        super();
        this.filters = filters;
        this.crawlDelay = crawlDelay;
    }

    public IURLFilter[] getFilters() {
        return filters;
    }
    
    public float getCrawlDelay() {
        return crawlDelay;
    }
}
