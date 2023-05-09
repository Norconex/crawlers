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
package com.norconex.importer.parser;

import java.io.Writer;
import java.util.List;

import com.norconex.importer.doc.Doc;

import lombok.NonNull;

/**
 * Implementations are responsible for parsing a document to
 * extract its text and metadata, as well as any embedded documents
 * (when applicable).
 */
public interface DocumentParser {

    /**
     * Initializes this parser, allowing caching of elements to improve re-use.
     * Not all parsers support all parse options and it is possible calling
     * this method on specific parsers to have no effect.
     * @param parseOptions parse options (never <code>null</code>)
     * @throws DocumentParserException problem initializing parser
     */
    void init(@NonNull ParseOptions parseOptions)
            throws DocumentParserException;

    /**
     * Parses a document.
     * @param doc importer document to parse
     * @param output where to store extracted or modified content of the
     *        supplied document
     * @return a list of first-level embedded documents, if any
     * @throws DocumentParserException problem parsing document
     */
    List<Doc> parseDocument(Doc doc, Writer output)
            throws DocumentParserException;
}
