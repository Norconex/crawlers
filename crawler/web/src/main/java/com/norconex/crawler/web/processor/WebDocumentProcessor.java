/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.crawler.web.processor;

import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.importer.doc.Doc;

/**
 * Custom processing (optional) performed on a document.  Can be used
 * just before of after a document has been imported.
 * @since 2.8.0 (moved from "com.norconex.crawler.web.doc" package)
 */
@FunctionalInterface
public interface WebDocumentProcessor {

	/**
	 * Processes a document.
	 * @param fetchClient HTTP fetch client
	 * @param doc the document
	 */
    void processDocument(HttpFetcher fetcher, Doc doc);
}
