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
package com.norconex.crawler.fs.doc.pipelines.importer.stages;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// TODO: Rewrite this test using the new CrawlSession/CrawlDocContext API.
// MockFsCrawlerBuilder.withCrawlContext() no longer exists.
// The test relied on CrawlDoc and old ImporterPipelineContext(CrawlContext, CrawlDoc)
// constructors that have been removed as part of the Hazelcast/cluster API redesign.
@Disabled("Needs rewrite for new CrawlSession/CrawlDocContext API")
class FolderPathsExtractorStageTest {

    @Test
    void testFetchExceptionWrapped() {
        // TODO: Implement using new crawl session mock infrastructure.
    }
}
