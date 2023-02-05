/* Copyright 2016-2022 Norconex Inc.
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

import org.apache.commons.lang3.StringUtils;

import lombok.Data;

/**
 * Configuration settings affecting how embedded documents are handled
 * by parsers.
 */
@Data
public class EmbeddedConfig {

    private String splitContentTypes;
    private String noExtractEmbeddedContentTypes;
    private String noExtractContainerContentTypes;

    public boolean isEmpty() {
        return StringUtils.isBlank(splitContentTypes)
                && StringUtils.isBlank(noExtractContainerContentTypes)
                && StringUtils.isBlank(noExtractEmbeddedContentTypes);
    }
}
