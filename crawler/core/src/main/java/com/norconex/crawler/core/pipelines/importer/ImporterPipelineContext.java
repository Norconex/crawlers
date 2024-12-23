/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.core.pipelines.importer;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.importer.response.ImporterResponse;

import lombok.Data;

/**
 * A context object for crawler pipelines dealing
 * with {@link ImporterResponse}.
 * This context is short-lived and can be redeclared in a pipeline
 * chain (i.e., the original context instance may be replaced
 * by one of the pipeline stages).
 */
@Data
public class ImporterPipelineContext {

    private final CrawlerContext crawlerContext;
    private final CrawlDoc doc;

    private ImporterResponse importerResponse;

    //TODO see if we can work with this instead of "flags"
    private boolean delete;

    //TODO needed? The orphan flag is stored in the document itself
    // Shall we store the deletion flag in the document as well??
    //private boolean orphan;

    /**
     * Whether a metadata fetch request was performed already. Based on whether
     * metadata fetch support is enabled via configuration
     * and we are now doing a document fetch request (which suggests
     * a METADATA request would have had to be performed).
     * @param currentDirective the current directive
     * @return <code>true</code> if the metadata directive was executed
     */
    public boolean isMetadataDirectiveExecuted(
            FetchDirective currentDirective) {
        // If both DOCUMENT and METADATA fetching were requested and the
        // current directive is DOCUMENT, then metadata had to be performed.
        return currentDirective == FetchDirective.DOCUMENT
                && FetchDirectiveSupport.isEnabled(
                        crawlerContext.getConfiguration()
                                .getMetadataFetchSupport());
    }

    /**
     * Whether a fetch directive has been enabled according to configuration.
     * That is, its use is either "required" or "optional".
     * @param directive fetch directive
     * @return <code>true</code> if the supplied directive is enabled
     */
    public boolean isFetchDirectiveEnabled(FetchDirective directive) {
        return (directive == FetchDirective.METADATA
                && FetchDirectiveSupport.isEnabled(
                        crawlerContext.getConfiguration()
                                .getMetadataFetchSupport()))
                || (directive == FetchDirective.DOCUMENT
                        && FetchDirectiveSupport.isEnabled(
                                crawlerContext.getConfiguration()
                                        .getDocumentFetchSupport()));
    }
}
