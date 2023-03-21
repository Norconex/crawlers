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
package com.norconex.crawler.fs.fetch.impl;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.fs.fetch.FileFetchRequest;

final class FileFetchUtil {

    private FileFetchUtil() {}

    static boolean referenceStartsWith(FileFetchRequest req, String... prefixes) {

        return Optional.ofNullable(req)
                .map(FileFetchRequest::getDoc)
                .map(CrawlDoc::getReference)
                .map(String::toLowerCase)
                .filter(ref -> StringUtils.startsWithAny(ref, prefixes))
                .isPresent();
    }



//    return startsWithIgnoreCase(
//            fetchRequest.getDoc().getReference(), "ftp://");

}
