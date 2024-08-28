/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations;

import java.util.function.BiConsumer;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.fetch.Fetcher;

/**
 * Optional custom processing of a document just before or just after
 * document has been imported by the Importer module. Set via
 * the crawler configuration.
 */
@FunctionalInterface
public interface DocumentConsumer extends BiConsumer<Fetcher<?, ?>, CrawlDoc> {

    /**
     * Processes a document.
     * @param fetcher document fetcher
     * @param doc crawl document
     */
    @Override
    void accept(Fetcher<?, ?> fetcher, CrawlDoc doc);
}
