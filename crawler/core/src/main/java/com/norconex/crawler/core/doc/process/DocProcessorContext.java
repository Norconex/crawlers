/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.process;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.tasks.CrawlerTaskContext;
import com.norconex.importer.response.ImporterResponse;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
class DocProcessorContext {
    private CrawlerTaskContext crawler;
    private CrawlDocContext docContext;
    private CrawlDoc doc;
    private ImporterResponse importerResponse;
    private boolean orphan;
    private boolean finalized;
}