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

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;

//REMOVE to make it a regular handler, but how to we keep the init around?
// and how about shut down?  We should probably add that to all handlers
// maybe to an interface as default methods? Or shall we use existing
// mechanism that scans and initiate classes? Yeah, probably best.

// Maybe rename RawTextExtractor?

/**
 * Implementations are responsible for interacting with a document to
 * either parse it, transform it, decorate it, filter it, etc.
 */
public interface DocumentHandler extends Consumer<DocContext>, Closeable {

    // THIS SHOULD BE DETECTABLE and be the base of doc consumers for
    // the importer.

    //TODO move this out of .parser.

    //TODO maybe pass Importer to method?
    default void init() throws IOException {}
    @Override
    default void close() throws IOException {}
    //default void destroy(Importer importer) {}

//    /**
//     * Initializes this parser, allowing caching of elements to improve re-use.
//     * Not all parsers support all parse options and it is possible calling
//     * this method on specific parsers to have no effect.
//     * @param parseOptions parse options (never <code>null</code>)
//     * @throws DocumentParserException problem initializing parser
//     */
//    void init(@NonNull ParseOptions parseOptions)
//            throws DocumentParserException;
//
//    /**
//     * Parses a document.
//     * @param doc importer document to parse
//     * @param output where to store extracted or modified content of the
//     *        supplied document
//     * @return a list of first-level embedded documents, if any
//     * @throws DocumentParserException problem parsing document
//     */
//    List<Doc> parseDocument(Doc doc, Writer output)
//            throws DocumentParserException;
}
