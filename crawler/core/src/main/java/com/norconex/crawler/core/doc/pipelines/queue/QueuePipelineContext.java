/* Copyright 2020-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.queue;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.doc.CrawlDocContext;

import lombok.Data;

/**
 * Hold necessary objects to a specific pipeline execution over a
 * {@link CrawlDocContext}.
 * This context is short-lived and can be redeclared in a pipeline
 * chain (i.e., the original context instance may be replaced
 * by one of the pipeline stages).
 */
@Data
public class QueuePipelineContext {
    private final Crawler crawler;
    private final CrawlDocContext docContext;
}
