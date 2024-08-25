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
package com.norconex.importer.handler.transformer.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;

import lombok.Data;
import lombok.NonNull;

@Data
public final class CopyOperation {

    /**
     * One or more source field to copy to target field. If omitted,
     * the entire document body is copied (use with care).
     */
    private final TextMatcher fieldMatcher = new TextMatcher();
    private final String toField;
    private final PropertySetter onSet;

    private CopyOperation(
            TextMatcher fieldMatcher, String toField, PropertySetter onSet
    ) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        this.toField = toField;
        this.onSet = onSet;
    }

    public static CopyOperation of(@NonNull String toField) {
        return of(null, toField, null);
    }

    public static CopyOperation of(
            TextMatcher fieldMatcher, @NonNull String toField
    ) {
        return of(fieldMatcher, toField, null);
    }

    @JsonCreator
    public static CopyOperation of(
            @JsonProperty("fieldMatcher") TextMatcher fieldMatcher,
            @JsonProperty("toField")
            @NonNull String toField,
            @JsonProperty("onSet") PropertySetter onSet
    ) {
        return new CopyOperation(fieldMatcher, toField, onSet);
    }
}