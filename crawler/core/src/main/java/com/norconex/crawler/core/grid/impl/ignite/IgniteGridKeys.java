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
package com.norconex.crawler.core.grid.impl.ignite;

/**
 * Constant keys specific to Ignite grid implementation.
 */
public final class IgniteGridKeys {

    static final String GLOBAL_CACHE = "global-cache";
    static final String CRAWLER_CONFIG = "crawler-config";
    static final String CRAWLER_BUILDER_FACTORY_CLASS =
            "crawler-builder-factory-class";
    public static final String RUN_ONCE_CACHE = "runonce-cache";

    private IgniteGridKeys() {
    }
}
