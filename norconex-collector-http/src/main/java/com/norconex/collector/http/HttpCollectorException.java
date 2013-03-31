package com.norconex.collector.http;

/**
 * Runtime exception for most unrecoverable issues thrown by HTTP Collector
 * classes.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class HttpCollectorException extends RuntimeException {

    private static final long serialVersionUID = -805913995358009121L;

    public HttpCollectorException() {
        super();
    }

    public HttpCollectorException(String message) {
        super(message);
    }

    public HttpCollectorException(Throwable cause) {
        super(cause);
    }

    public HttpCollectorException(String message, Throwable cause) {
        super(message, cause);
    }

}
