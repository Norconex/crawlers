/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.crawler.core.pipeline.importer;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.pipeline.DocumentPipelineContext;
import com.norconex.importer.response.ImporterResponse;

import lombok.Data;

/**
 * A context object for crawl session pipelines dealing
 * with {@link ImporterResponse}.
 */
@Data
public class ImporterPipelineContext extends DocumentPipelineContext {

    private ImporterResponse importerResponse;

    /**
     * Constructor.
     * @param crawler the crawler
     * @param document current crawl document
     */
    public ImporterPipelineContext(Crawler crawler, CrawlDoc document) {
        super(crawler, document);
    }
}
