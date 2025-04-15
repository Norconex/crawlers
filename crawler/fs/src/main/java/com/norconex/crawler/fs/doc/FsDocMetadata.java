/* Copyright 2013-2025 Norconex Inc.
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
package com.norconex.crawler.fs.doc;

import static com.norconex.crawler.core.doc.CrawlDocMetaConstants.PREFIX;

public final class FsDocMetadata {

    public static final String PATH = PREFIX + "path";

    public static final String FILE_SIZE = PREFIX + "fileSize";

    //TODO make part of crawler-core?
    public static final String LAST_MODIFIED = PREFIX + "lastModified";

    public static final String ACL = PREFIX + "acl";

    private FsDocMetadata() {
    }
}
