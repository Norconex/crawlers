/* Copyright 2010-2018 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.http.doc;

import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.importer.doc.ImporterDocument;

//TODO consider dropping since it just brings HttpMetadata cast.
public class HttpDocument extends ImporterDocument {

    /**
     *
     * @param reference
     * @param content
     * @since 3.0.0
     */
    public HttpDocument(String reference, CachedInputStream content) {
        super(reference, content, new HttpMetadata(reference));
    }

    public HttpDocument(String reference, CachedInputStream content,
            HttpMetadata metadata) {
        super(reference, content, metadata);
        // TODO Auto-generated constructor stub
    }

    public HttpDocument(String reference, CachedStreamFactory streamFactory) {
        super(reference, streamFactory, new HttpMetadata(reference));
        // TODO Auto-generated constructor stub
    }

    public HttpDocument(ImporterDocument importerDocument) {
        super(importerDocument.getReference(),
                importerDocument.getInputStream(),
                new HttpMetadata(importerDocument.getMetadata()));
        setReference(importerDocument.getReference());
        setContentType(importerDocument.getContentType());
        setContentEncoding(importerDocument.getContentEncoding());
    }

    @Override
    public HttpMetadata getMetadata() {
        return (HttpMetadata) super.getMetadata();
    }
}
