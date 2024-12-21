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
package com.norconex.crawler.web.operations.delay.impl;

import java.time.Duration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class DelayReferencePattern {
    private final String pattern;
    private final Duration delay;

    @JsonCreator
    public DelayReferencePattern(
            @JsonProperty("pattern") String pattern,
            @JsonProperty("delay") Duration delay) {
        this.pattern = pattern;
        this.delay = delay;
    }

    @JsonIgnore
    public boolean matches(String reference) {
        return reference.matches(pattern);
    }
}
