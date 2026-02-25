/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.stubs;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.importer.doc.Doc;

public final class DocStubber {

    public static final String CRAWLDOC_CONTENT = "Some content.";
    public static final String CRAWLDOC_CONTENT_MD5 =
            "b8ab309a6b9a3f448092a136afa8fa25";

    private DocStubber() {
    }

    public static Doc doc(String ref) {
        return doc(ref, CRAWLDOC_CONTENT);
    }

    public static Doc doc(
            String ref, String content, Object... metaKeyValues) {
        @SuppressWarnings("resource")
        var doc = new Doc(ref)
                .setInputStream(new CachedStreamFactory().newInputStream(
                        IOUtils.toInputStream(content, UTF_8)));
        if (ArrayUtils.isNotEmpty(metaKeyValues)) {
            doc.getMetadata().loadFromMap(MapUtil.toMap(metaKeyValues));
        }
        return doc;
    }
}
