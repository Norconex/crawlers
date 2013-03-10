package com.norconex.collector.http.crawler;

public enum CrawlStatus { 
    OK, 
    REJECTED, 
    ERROR, 
    UNMODIFIED, 
    TOO_DEEP, 
    DELETED, 
    NOT_FOUND, 
    BAD_STATUS
}