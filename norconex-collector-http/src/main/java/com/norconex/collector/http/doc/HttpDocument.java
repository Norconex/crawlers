/* Copyright 2010-2019 Norconex Inc.
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

import static com.norconex.collector.http.doc.HttpMetadata.COLLECTOR_URL;

import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.importer.doc.ImporterDocument;

//TODO consider dropping since it just brings HttpMetadata cast.

//TODO forcing to pass COLLECTOR_URL that way is best?
public class HttpDocument extends ImporterDocument {

    /**
     * Creates a new HTTP document.
     * @param reference document reference
     * @param content document content
     * @since 3.0.0
     */
    public HttpDocument(String reference, CachedInputStream content) {
        super(reference, content, new HttpMetadata(reference));
    }

    public HttpDocument(String reference, CachedInputStream content,
            HttpMetadata metadata) {
        super(reference, content, metadata);
        getMetadata().set(COLLECTOR_URL, reference);
    }

    public HttpDocument(String reference, CachedStreamFactory streamFactory) {
        super(reference, streamFactory, new HttpMetadata(reference));
    }

    public HttpDocument(ImporterDocument importerDocument) {
        super(importerDocument.getReference(),
                importerDocument.getInputStream(),
                new HttpMetadata(importerDocument.getMetadata()));
        getMetadata().set(COLLECTOR_URL, importerDocument.getReference());
        setReference(importerDocument.getReference());
        setContentType(importerDocument.getContentType());
        setContentEncoding(importerDocument.getContentEncoding());
    }

    @Override
    public HttpMetadata getMetadata() {
        return (HttpMetadata) super.getMetadata();
    }
}
