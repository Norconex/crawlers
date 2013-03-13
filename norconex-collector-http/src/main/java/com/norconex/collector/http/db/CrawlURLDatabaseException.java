package com.norconex.collector.http.db;

public class CrawlURLDatabaseException extends RuntimeException {

    
    private static final long serialVersionUID = 5416591514078326431L;

    public CrawlURLDatabaseException() {
        super();
    }

    public CrawlURLDatabaseException(String message) {
        super(message);
    }

    public CrawlURLDatabaseException(Throwable cause) {
        super(cause);
    }

    public CrawlURLDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

}
