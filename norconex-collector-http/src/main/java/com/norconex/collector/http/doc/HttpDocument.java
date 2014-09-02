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
package com.norconex.collector.http.doc;

import com.norconex.importer.doc.ImporterDocument;

public class HttpDocument extends ImporterDocument {

    private static final long serialVersionUID = 4376740210800410675L;
//    private final String url;
//    private final File localFile;
//    private final HttpMetadata metadata;

//    private final HttpDocCrawl httpReference;
    
//    private String originalReference;
//    
//    public HttpDocument(HttpDocCrawl httpReference) {
//        super(httpReference.getReference(), new HttpMetadata(
//                httpReference.getReference()));
//        this.httpReference = httpReference;
//    }

    public HttpDocument(String reference) {
        super(reference, new HttpMetadata(reference));
        
        
//        private String reference;
//        private Content content;
//        private final ImporterMetadata metadata;
//        private ContentType contentType;
//        private String contentEncoding;
    }

    public HttpDocument(ImporterDocument importerDocument) {
        super(importerDocument.getReference());
        setReference(importerDocument.getReference());
        setContent(importerDocument.getContent());
        getMetadata().putAll(importerDocument.getMetadata());
        setContentType(importerDocument.getContentType());
        setContentEncoding(importerDocument.getContentEncoding());
    }
    
//    
//    /**
//     * @return the httpReference
//     */
//    public HttpDocCrawl getHttpReference() {
//        return httpReference;
//    }
//    
    
    
//    
//    
//    public HttpDocument(String url, File localFile) {
//        super(new http);
//        this.url = url;
//        this.localFile = localFile;
//        this.metadata = new HttpMetadata(url);
//    }

//    public String getUrl() {
//        //TODO make it point to meta URL or keep separate to distinguish
//        //between original URL and potentiallly overwritten one?
//        return url;
//    }
//
//    public File getLocalFile() {
//        return localFile;
//    }


//    public String getOriginalReference() {
//        return originalReference;
//    }
//
//    public void setOriginalReference(String originalReference) {
//        this.originalReference = originalReference;
//    }




    public HttpMetadata getMetadata() {
        return (HttpMetadata) super.getMetadata();
    }
}
