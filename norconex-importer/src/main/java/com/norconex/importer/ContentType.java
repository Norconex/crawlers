/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Importer.
 * 
 * Norconex Importer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Importer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Importer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.importer;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represent a document content type (also called MIME-Type or Media Type).
 * @author Pascal Essiembre
 */
public final class ContentType implements Serializable {
  //TODO make part of norconex.commons.lang ????

    private static final long serialVersionUID = 6416074869536512030L;
    private String contentType;
    private static final Map<String, ContentType> REGISTRY = 
        new HashMap<String, ContentType>();

    public static final ContentType HTML = new ContentType("text/html");
    public static final ContentType PDF = new ContentType("application/pdf");
    public static final ContentType XPDF = new ContentType("application/x-pdf");
    
    
    private ContentType(String contentType) {
        super();
        this.contentType = contentType;
        REGISTRY.put(contentType, this);
    }

    /**
     * Creates a new content type.  Returns an existing instance if the 
     * same content type is requested more than once.
     * @param contentType the official media type name
     * @return content type
     */
    public static ContentType newContentType(String contentType) {
        ContentType type = REGISTRY.get(contentType);
        if (type != null) {
            return type;
        }
        return new ContentType(contentType);
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((contentType == null) ? 0 : contentType.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ContentType other = (ContentType) obj;
        if (contentType == null) {
            if (other.contentType != null) {
                return false;
            }
        } else if (!contentType.equals(other.contentType)) {
            return false;
        }
        return true;
    }

    /**
     * Returns a string representation of the content-type.
     */
    @Override
    public String toString() {
        return contentType;
    }
    
}
