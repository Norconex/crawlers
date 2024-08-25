/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.importer.util;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.doc.DocContext;

public final class MatchUtil {

    private MatchUtil() {
    }

    /**
     * Null-safe matches a document record content type.
     * @param matcher text matcher
     * @param docRecord the document record on which to assess the content type
     * @return <code>true</code> if matches or if either arguments or content
     *      type are <code>null</code>
     */
    public static boolean matchesContentType(
            TextMatcher matcher, DocContext docRecord
    ) {
        if (docRecord == null) {
            return true;
        }
        return matchesContentType(matcher, docRecord.getContentType());
    }

    /**
     * Null-safe matches a content type.
     * @param matcher text matcher
     * @param contentType the content type to match
     * @return <code>true</code> if matches or if either arguments are
     *      <code>null</code>
     */
    public static boolean matchesContentType(
            TextMatcher matcher, ContentType contentType
    ) {
        if (matcher == null || contentType == null) {
            return true;
        }
        return matcher.matches(contentType.toBaseTypeString());
    }
}
