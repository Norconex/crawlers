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
