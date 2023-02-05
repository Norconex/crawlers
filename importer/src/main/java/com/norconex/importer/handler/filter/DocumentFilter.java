/* Copyright 2010-2022 Norconex Inc.
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
package com.norconex.importer.handler.filter;

import java.io.InputStream;

import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandler;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

/**
 * Filters documents.  Rejected documents are no longer processed.
 */
public interface DocumentFilter extends ImporterHandler {

    /**
     * Whether to accepts a document.
     * @param doc the document to evaluate
     * @param input document content
     * @param parseState whether the document has been parsed already or not (a
     *        parsed document should normally be text-based)
     * @return <code>true</code> if document is accepted
     * @throws ImporterHandlerException problem reading the document
     */
    boolean acceptDocument(
            HandlerDoc doc, InputStream input, ParseState parseState)
        throws ImporterHandlerException;

    //MAYBE: have a RejectionCause returned instead of boolean?

}
