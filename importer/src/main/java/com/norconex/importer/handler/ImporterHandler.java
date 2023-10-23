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
package com.norconex.importer.handler;

import com.norconex.importer.handler.splitter.DocumentSplitter;
import com.norconex.importer.handler.transformer.DocumentTransformer;

/**
 * <p>Identifies a class as being an import handler.  Handlers performs specific
 * tasks on the importing content (other than parsing to extract raw content).
 * They can be invoked before or after a document is parsed.  There are
 * four types of handlers currently supported:</p>
 * <ul>
 *   <li>{@link DocumentFilter}: accepts or reject an incoming document.</li>
 *   <li>{@link DocumentTagger}: modifies a document metadata.</li>
 *   <li>{@link DocumentTransformer}: modifies a document content.</li>
 *   <li>{@link DocumentSplitter}: splits a document into multiple ones.</li>
 * </ul>
 * @param <T> type of object passed to implementing handler
 */

//TODO REMOVE? STILL NEEDED?
public interface ImporterHandler {
    // Act as marker
}

//MAYBE?
//public interface ImporterHandler<T> {
//    void handle(T t) throws ImporterHandlerException;
//}
