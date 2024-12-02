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
package com.norconex.crawler.core.commands.crawl.task.operations.checksum.impl;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.commands.crawl.task.operations.checksum.BaseChecksummerConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Configuration for {@link Md5DocumentChecksummer}.
 */
@Data
@Accessors(chain = true)
public class Md5DocumentChecksummerConfig extends BaseChecksummerConfig {

    /**
     * Matcher of one or more fields to use to make up the checksum.
     */
    private final TextMatcher fieldMatcher = new TextMatcher();

    public Md5DocumentChecksummerConfig setFieldMatcher(
            TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        return this;
    }

    /**
     * Whether we are combining the fields and content checksums.
     */
    private boolean combineFieldsAndContent;
}
