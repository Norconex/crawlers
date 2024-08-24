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
package com.norconex.crawler.web.doc.operations.scope;

import org.apache.commons.lang3.StringUtils;

public final class UrlScope {

    private static final UrlScope IN_SCOPE = new UrlScope(null);

    private final String outOfScopeReason;

    private UrlScope(String outOfScopeReason) {
        this.outOfScopeReason = outOfScopeReason;
    }

    public static UrlScope in() {
        return IN_SCOPE;
    }
    public static UrlScope out(String reason) {
        return new UrlScope(StringUtils.firstNonBlank(
                reason, "Unspecified reason."));
    }

    public boolean isInScope() {
        return outOfScopeReason == null;
    }
    public String outOfScopeReason() {
        return outOfScopeReason;
    }
}
