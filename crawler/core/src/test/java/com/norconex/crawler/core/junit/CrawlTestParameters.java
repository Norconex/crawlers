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

import java.nio.file.Path;

import org.junit.jupiter.api.extension.ExtensionContext;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.core.CrawlConfig;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CrawlTestParameters {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create("crawlTestNamespace");
    private static final String PARAMS_KEY = "crawlTestParameters";

    private Crawler crawler;
    private CrawlConfig crawlConfig;
    private CrawlContext crawlContext;
    private MemoryCommitter memoryCommitter;
    private Path workDir;

    public static void set(ExtensionContext ctx, CrawlTestParameters params) {
        ctx.getStore(NAMESPACE).put(PARAMS_KEY, params);
    }

    public static CrawlTestParameters get(ExtensionContext ctx) {
        return ctx.getStore(NAMESPACE).get(
                PARAMS_KEY, CrawlTestParameters.class);
    }
}
