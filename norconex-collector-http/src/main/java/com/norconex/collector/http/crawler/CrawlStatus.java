package com.norconex.collector.http.crawler;

import java.io.Serializable;

public enum CrawlStatus implements Serializable { 
    OK, 
    REJECTED, 
    ERROR, 
    UNMODIFIED, 
    TOO_DEEP, 
    DELETED, 
    NOT_FOUND, 
    BAD_STATUS
}