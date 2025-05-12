/* Copyright 2024-2025 Norconex Inc.
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

import java.util.List;

import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

import com.norconex.grid.core.GridConnector;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class CrawlTestInvocationContext implements TestTemplateInvocationContext {

    private final Class<? extends GridConnector> gridConnectorClass;
    private final CrawlTest annotation;

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(
                new CrawlTestExtensionCallbacks(
                        gridConnectorClass, annotation),
                new CrawlTestParameterResolver());
    }

    //    @Override
    //    public String getDisplayName(int invocationIndex) {
    //        if (gridConnectorClass.isAssignableFrom(IgniteGridConnector.class)) {
    //            return "🔥On Ignite Grid";
    //        }
    //        if (gridConnectorClass
    //                .isAssignableFrom(LocalGridConnector.class)) {
    //            return "📂On Local Grid";
    //        }
    //        return String.format("On grid: %s", gridConnectorClass.getSimpleName());
    //    }
}
