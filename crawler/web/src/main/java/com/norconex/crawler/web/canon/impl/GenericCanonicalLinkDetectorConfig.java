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
package com.norconex.crawler.web.canon.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.ContentType;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GenericCanonicalLinkDetectorConfig {
    private final List<ContentType> contentTypes = new ArrayList<>();

    public GenericCanonicalLinkDetectorConfig(
            List<ContentType> defaultContentTypes) {
        contentTypes.addAll(defaultContentTypes);
    }

    public List<ContentType> getContentTypes() {
        return Collections.unmodifiableList(contentTypes);
    }
    /**
     * Sets the content types on which to perform canonical link detection.
     * @param contentTypes content types
     */
    public GenericCanonicalLinkDetectorConfig setContentTypes(
            List<ContentType> contentTypes) {
        CollectionUtil.setAll(this.contentTypes, contentTypes);
        return this;
    }
}
