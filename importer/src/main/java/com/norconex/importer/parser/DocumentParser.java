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
package com.norconex.importer.parser;

import java.io.Writer;
import java.util.List;

import com.norconex.importer.doc.Doc;

/**
 * Implementations are responsible for parsing a document to
 * extract its text and metadata, as well as any embedded documents
 * (when applicable).
 * @see DocumentParserFactory
 */
public interface DocumentParser {

    /**
     * Parses a document.
     * @param doc importer document to parse
     * @param output where to store extracted or modified content of the
     *        supplied document
     * @return a list of first-level embedded documents, if any
     * @throws DocumentParserException problem parsing document
     */
    List<Doc> parseDocument(
            Doc doc, Writer output) throws DocumentParserException;
}
