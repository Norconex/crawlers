/* Copyright 2010-2014 Norconex Inc.
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
package com.norconex.collector.http.pipeline.importer;

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.commons.lang.file.ContentType;

/**
 * @author Pascal Essiembre
 *
 */
/*default*/ final class HttpImporterPipelineUtil {

    /**
     * Constructor.
     */
    private HttpImporterPipelineUtil() {
    }

    //TODO consider making public, putting content type and encoding in CORE.
    public static void applyMetadataToDocument(HttpDocument doc) {
        if (doc.getContentType() == null) {
            doc.setContentType(ContentType.valueOf(
                    doc.getMetadata().getString(
                            HttpMetadata.COLLECTOR_CONTENT_TYPE)));
            doc.setContentEncoding(doc.getMetadata().getString(
                    HttpMetadata.COLLECTOR_CONTENT_ENCODING));
        }
    }
    
    public static void enhanceHTTPHeaders(HttpMetadata metadata) {
        if (StringUtils.isNotBlank(
                metadata.getString(HttpMetadata.COLLECTOR_CONTENT_TYPE))) {
            return;
        }
        
        String contentType = metadata.getString(HttpMetadata.HTTP_CONTENT_TYPE);
        if (StringUtils.isBlank(contentType)) {
            for (String key : metadata.keySet()) {
                if (StringUtils.endsWith(key, HttpMetadata.HTTP_CONTENT_TYPE)) {
                    contentType = metadata.getString(key);
                }
            }
        }
        if (StringUtils.isNotBlank(contentType)) {
            String mimeType = contentType.replaceFirst("(.*?)(;.*)", "$1");
            String charset = contentType.replaceFirst("(.*?)(; )(.*)", "$3");
            charset = charset.replaceFirst("(charset=)(.*)", "$2");
            metadata.addString(HttpMetadata.COLLECTOR_CONTENT_TYPE, mimeType);
            metadata.addString(HttpMetadata.COLLECTOR_CONTENT_ENCODING, charset);
        }
    }
}
