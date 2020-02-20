/* Copyright 2010-2020 Norconex Inc.
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

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.DocInfo;
import com.norconex.importer.doc.Doc;

//TODO consider dropping since it just brings HttpDocMetadata cast.

//TODO forcing to pass COLLECTOR_URL that way is best?
/**
 *
 * @author Pascal Essiembre
 * @since 3.0.0, renamed from HttpDocument
 */
public class HttpDoc extends CrawlDoc {

    public HttpDoc(Doc doc) {
        super(doc.getDocInfo(),
                doc.getInputStream(),
                new HttpDocMetadata(doc.getMetadata()));
    }

    public HttpDoc(String reference, CachedInputStream content) {
        super(new HttpDocInfo(reference), content, null);
    }

    public HttpDoc(DocInfo docDetails, CachedInputStream content,
            Properties metadata) {
        super(docDetails, content, metadata);
    }

    public HttpDoc(DocInfo docDetails, CachedInputStream content) {
        super(docDetails, content);
    }
}
