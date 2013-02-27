package com.norconex.importer;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class ContentType implements Serializable {

    //TODO make part of norconex.commons.lang ????
    
    private static final long serialVersionUID = 6416074869536512030L;
    private String contentType;
    private static final Map<String, ContentType> registry = 
        new HashMap<String, ContentType>();

    public static final ContentType HTML = new ContentType("text/html");
    public static final ContentType PDF = new ContentType("application/pdf");
    public static final ContentType XPDF = new ContentType("application/x-pdf");
    
    
    private ContentType(String contentType) {
        super();
        this.contentType = contentType;
        registry.put(contentType, this);
    }

    public static ContentType newContentType(String contentType) {
        ContentType type = registry.get(contentType);
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
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ContentType other = (ContentType) obj;
        if (contentType == null) {
            if (other.contentType != null)
                return false;
        } else if (!contentType.equals(other.contentType))
            return false;
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
