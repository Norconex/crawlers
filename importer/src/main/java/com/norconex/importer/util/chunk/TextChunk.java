/* Copyright 2023 Norconex Inc.
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
package com.norconex.importer.util.chunk;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class TextChunk {
    /**
     * The field name where the text is coming from. <code>null</code>
     * if the text comes from the document content instead.
     */
    private final String field; // null if content
    /**
     * On multi-valued field, the index of the value currently processed.
     */
    private final int fieldValueIndex;
    /** Index of the text portion currently processed. */
    private final int chunkIndex;
    /**
     * Full or partial text depending whether the maximum read size was
     * reached and it needed to be sent in text chunks.
     */
    private final String text;
}