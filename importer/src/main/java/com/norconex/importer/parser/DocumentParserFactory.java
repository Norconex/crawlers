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

import com.norconex.commons.lang.file.ContentType;

/**
 * Factory providing document parsers for documents.
 */
public interface DocumentParserFactory {

    /**
     * Gets a document parser, optionally based on its reference or content
     * type.
     * @param documentReference document reference
     * @param contentType content type
     * @return document parser
     */
    DocumentParser getParser(
            String documentReference, ContentType contentType);
}
