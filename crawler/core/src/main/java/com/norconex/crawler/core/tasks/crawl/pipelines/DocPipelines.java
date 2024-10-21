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
package com.norconex.crawler.core.tasks.crawl.pipelines;

import com.norconex.crawler.core.tasks.crawl.pipelines.committer.CommitterPipeline;
import com.norconex.crawler.core.tasks.crawl.pipelines.importer.ImporterPipeline;
import com.norconex.crawler.core.tasks.crawl.pipelines.queue.QueuePipeline;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DocPipelines {

    private final QueuePipeline queuePipeline;
    private final ImporterPipeline importerPipeline;
    private final CommitterPipeline committerPipeline;

}
