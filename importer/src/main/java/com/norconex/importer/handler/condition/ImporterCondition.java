/* Copyright 2021-2023 Norconex Inc.
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
package com.norconex.importer.handler.condition;

import java.io.InputStream;

import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

/**
 * A condition usually used in XML flow creation when configuring
 * importer handlers.
 */
@FunctionalInterface
public interface ImporterCondition {

    //TODO extend Predicate and replace method or have a default one?

    /**
     * Tests a given document.
     * @param doc the document to test
     * @param input document content
     * @param parseState whether the document has been parsed already or not (a
     *        parsed document should normally be text-based)
     * @return <code>true</code> if the condition evaluates as such
     * @throws ImporterHandlerException problem reading the document
     */
    boolean testDocument(
            HandlerDoc doc, InputStream input, ParseState parseState)
        throws ImporterHandlerException;

}
