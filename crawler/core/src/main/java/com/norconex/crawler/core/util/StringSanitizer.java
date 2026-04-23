/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.util;

import lombok.Builder;

//TODO nx commons lang?
/**
 * Sanitizes a string by stripping all characters but those identified.
 */
@Builder(builderClassName = "Builder")
public final class StringSanitizer { //NOSONAR

    public static final StringSanitizer DEFAULT = StringSanitizer.builder()
            .alpha()
            .numeric()
            .underscore()
            .hypen()
            .build();

    private final boolean alpha;
    private final boolean numeric;
    private final boolean underscore;
    private final boolean hypen;

    public String sanitize(String str) {
        var allowed = new StringBuilder();
        if (alpha) {
            allowed.append("A-Za-z");
        }
        if (numeric) {
            allowed.append("0-9");
        }
        if (underscore) {
            allowed.append("_");
        }
        if (hypen) {
            allowed.append("-");
        }
        var pattern = "[^" + allowed + "]";
        return str.replaceAll(pattern, "");
    }

    public static class Builder {
        public Builder alpha() {
            alpha = true;
            return this;
        }

        public Builder numeric() {
            numeric = true;
            return this;
        }

        public Builder underscore() {
            underscore = true;
            return this;
        }

        public Builder hypen() {
            hypen = true;
            return this;
        }
    }
}
