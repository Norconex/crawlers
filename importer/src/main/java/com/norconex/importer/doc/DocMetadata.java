/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.importer.doc;

/**
 * <p>
 * Constants for common metadata field names typically associated
 * with a document and often set on {@link Doc#getMetadata()}.
 * </p>
 * <p>
 * While those may originally be set by the importer, it is possible for
 * implementors to overwrite, rename, or even delete them from a document
 * metadata.
 * </p>
 */
public final class DocMetadata {

    //TODO move to Doc/DocRecord and delete this class.

    //MAYBE DELETE these if they can be referenced from DocRecord?
    //    (still have them as metadata, just no longer need constants).
    // maybe we could document all DocRecord properties are copied as
    // metadata automatically using X reflection pattern?

    private static final String PREFIX = "document.";

    /** Document unique reference (also called "id", "primary key", etc.). */
    public static final String REFERENCE = PREFIX + "reference";
    /** Document content type (also called "media type", or "mime type"). */
    public static final String CONTENT_TYPE = PREFIX + "contentType";
    /** Document character encoding. */
    public static final String CONTENT_ENCODING = PREFIX + "contentEncoding";
    /** Document content family (general categorization of content types). */
    public static final String CONTENT_FAMILY = PREFIX + "contentFamily";
    /** Document language. */
    public static final String LANGUAGE = PREFIX + "language";
    /** Original language when translated. */
    public static final String TRANSLATED_FROM = PREFIX + "translatedFrom";
    /** Generated title. */
    public static final String GENERATED_TITLE = PREFIX + "generatedTitle";
    /** Date processed by the Importer. */
    public static final String IMPORTED_DATE = PREFIX + "importedDate";

    static final String EMBEDDED_PREFIX = PREFIX + "embedded.";
    /** All references to parents of an embedded document (first is top-one). */
    public static final String EMBEDDED_PARENT_REFERENCES =
            EMBEDDED_PREFIX + "parent.reference";
    /** Relative reference to this document within its parent. */
    public static final String EMBEDDED_REFERENCE =
            EMBEDDED_PREFIX + "reference";
    /** Type of embedded file (from a zip, a word doc, etc.). */
    public static final String EMBEDDED_TYPE =
            EMBEDDED_PREFIX + "type";
    /** Zero-based index of this embedded document in relation to siblings. */
    public static final String EMBEDDED_INDEX =
            EMBEDDED_PREFIX + "index";

    private DocMetadata() {
    }
}
