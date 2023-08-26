/* Copyright 2015-2023 Norconex Inc.
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
package com.norconex.crawler.web.canon;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;

/**
 * <p>Detects and return any canonical URL found in documents, whether from
 * the HTTP headers (metadata), or from a page content (usually HTML).
 * Documents having a canonical URL reference in them are rejected in favor
 * of the document represented by the canonical URL.</p>
 *
 * <p>When metadata fetching is enabled via
 * {@link WebCrawlerConfig#getMetadataFetchSupport()},
 * a page won't be downloaded if a canonical link is found in the HTTP headers
 * (saving bandwidth and
 * processing). If not used, or if no canonical link was found, an attempt
 * will be made against the HTTP headers obtained (if any) just after fetching
 * a document. If no canonical link was found there, then the content
 * is evaluated.</p>
 *
 * <p>A canonical link found to be the same as the current page reference is
 * ignored.</p>
 *
 * @since 2.2.0
 */
//@Schema(
//        name = "CanonicalLinkDetector",
//
//        subTypes = {
//                GenericCanonicalLinkDetector.class
//        },
//        discriminatorProperty = "class"
//      ,
//        discriminatorMapping = {
//                @DiscriminatorMapping(
//                        value = "com.norconex.crawler.web.canon.impl.GenericCanonicalLinkDetector",
//                        schema = GenericCanonicalLinkDetector.class)
//        }
//)

//@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY
//)//   , property = "class") // <-- needed?

//MAYBE implement PropertyCustomizer?

//TODO automatic polymorphism via detection. See:
// https://github.com/springdoc/springdoc-openapi/issues/1334#issuecomment-1424534821
//@Schema







//@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "class")
//@JsonSubTypes({
//  @JsonSubTypes.Type(
//          value = GenericCanonicalLinkDetector.class ,
//          name = "com.norconex.crawler.web.canon.impl.GenericCanonicalLinkDetector"),
//  @JsonSubTypes.Type(
//          value = DummyCanonicalLinkDetector.class ,
//          name = "com.norconex.crawler.web.canon.impl.DummyCanonicalLinkDetector"),
//})
//@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY,  property = "structure_type")
//@JsonTypeInfo(
//        use = JsonTypeInfo.Id.CLASS,
//        include = JsonTypeInfo.As.PROPERTY,
//        property = "@class")

//@JsonTypeInfo(
//        use = JsonTypeInfo.Id.CLASS,
//        include = JsonTypeInfo.As.PROPERTY,
//        property = "class")

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = As.EXISTING_PROPERTY,
    property = "class",
    visible = true
)
public interface CanonicalLinkDetector {

    /**
     * Detects from metadata gathered so far, which when invoked, is
     * normally the HTTP header values.
     * @param reference document reference
     * @param metadata metadata object containing HTTP headers
     * @return the detected canonical URL or <code>null</code> if none is found.
     */
    String detectFromMetadata(String reference, Properties metadata);

    /**
     * Detects from a document content the presence of a canonical URL.
     * This occur before a document gets parsed and may apply to only
     * a few content types.
     * @param reference document reference
     * @param is the document content input stream
     * @param contentType the document content type
     * @return the detected canonical URL or <code>null</code> if none is found.
     * @throws IOException problem reading content
     */
    String detectFromContent(
            String reference, InputStream is, ContentType contentType)
                    throws IOException;
}
