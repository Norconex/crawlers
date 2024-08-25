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
package com.norconex.crawler.core.fetch;

/**
 * Indicates whether a given fetch directive should be required to execute,
 * optional, or disabled.
 */
public enum FetchDirectiveSupport {
    DISABLED, OPTIONAL, REQUIRED;

    public boolean is(FetchDirectiveSupport support) {
        // considers null as disabled.
        return (this == DISABLED && support == null)
                || (this == support);
    }

    public static boolean isEnabled(FetchDirectiveSupport support) {
        return support == FetchDirectiveSupport.OPTIONAL
                || support == FetchDirectiveSupport.REQUIRED;
    }
}