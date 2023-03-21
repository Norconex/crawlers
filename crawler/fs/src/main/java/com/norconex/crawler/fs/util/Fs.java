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
package com.norconex.crawler.fs.util;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.pipeline.AbstractPipelineContext;
import com.norconex.crawler.fs.crawler.FsCrawlerConfig;
import com.norconex.crawler.fs.pipeline.importer.FsImporterPipelineContext;

public final class Fs {

    private Fs() {}

    public static FsCrawlerConfig config(CrawlerConfig cfg) {
        return (FsCrawlerConfig) cfg;
    }
    public static FsCrawlerConfig config(AbstractPipelineContext ctx) {
        return (FsCrawlerConfig) ctx.getConfig();
    }
    public static FsCrawlerConfig config(Crawler crawler) {
        return (FsCrawlerConfig) crawler.getCrawlerConfig();
    }

    public static FsImporterPipelineContext context(
            AbstractPipelineContext ctx) {
        return (FsImporterPipelineContext) ctx;
    }
}
