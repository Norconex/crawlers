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
package com.norconex.crawler.core.junit;

import org.junit.jupiter.api.extension.ExtensionContext;

public final class JunitStore {
    private JunitStore() {
    }

    public static void set(ExtensionContext ctx, String key, Object value) {
        ctx.getStore(ExtensionContext.Namespace.GLOBAL).put(key, value);
    }

    public static void remove(ExtensionContext ctx, String key) {
        ctx.getStore(ExtensionContext.Namespace.GLOBAL).remove(key);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(ExtensionContext ctx, String key) {
        return (T) ctx
                .getStore(ExtensionContext.Namespace.GLOBAL)
                .get(key);
    }
}
