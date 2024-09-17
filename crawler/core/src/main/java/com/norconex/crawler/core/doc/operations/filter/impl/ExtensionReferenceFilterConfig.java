/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations.filter.impl;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Filters a reference based on a comma-separated list of extensions.
 * Extensions are typically the last characters of a file name, after the
 * last dot.
 * </p>
 */
@Data
@Accessors(chain = true)
public class ExtensionReferenceFilterConfig {

    private boolean ignoreCase;
    private final Set<String> extensions = new HashSet<>();
    private OnMatch onMatch = OnMatch.INCLUDE;

    public Set<String> getExtensions() {
        return Collections.unmodifiableSet(extensions);
    }

    public ExtensionReferenceFilterConfig setExtensions(
            Set<String> extensions) {
        CollectionUtil.setAll(this.extensions, extensions);
        return this;
    }
}
