/* Copyright 2010-2021 Norconex Inc.
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
package com.norconex.collector.http.processor;

import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.importer.doc.Doc;

/**
 * Custom processing (optional) performed on a document.  Can be used
 * just before of after a document has been imported.
 * @author Pascal Essiembre
 * @since 2.8.0 (moved from "com.norconex.collector.http.doc" package)
 */
@FunctionalInterface
public interface IHttpDocumentProcessor {

	/**
	 * Processes a document.
	 * @param fetchClient HTTP fetch client
	 * @param doc the document
	 */
    void processDocument(HttpFetchClient fetchClient, Doc doc);
}
