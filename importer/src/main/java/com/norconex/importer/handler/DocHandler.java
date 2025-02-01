/* Copyright 2025 Norconex Inc.
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

import java.io.IOException;

/**
 * Document Handler are responsible for interacting with a document to
 * either parse it, transform it, decorate it, split it, translate it, filter
 * it, etc.
 */
@FunctionalInterface
public interface DocHandler {
    /**
     * Initialize any required resources.
     * Invoked once per Importer instance, by the Importer.
     * @throws IOException An I/O exception of some sort has occurred
     */
    default void init() throws IOException {
    }

    /**
     * Perform any operations on a document.
     * @param docHandlerContext the document handling context
     * @return <code>false</code> to stop processing and reject a document or
     * <code>true</code> to proceed to the next configured handler.
     * @throws IOException An I/O exception of some sort has occurred
     */
    boolean handle(DocHandlerContext docHandlerContext) throws IOException;

    /**
     * Closes any required resources.
     * Invoked once per Importer instance, by the Importer.
     * @throws IOException An I/O exception of some sort has occurred
     */
    default void close() throws IOException {
    }
}
