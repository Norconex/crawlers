/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.importer;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.tasks.crawl.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.web.robot.RobotsMeta;

import lombok.Data;

@Data
public class WebImporterPipelineContext extends ImporterPipelineContext {

    private RobotsMeta robotsMeta;

    /**
     * Constructor creating a copy of supplied context.
     * @param ipc the item to be copied
     * @since 2.8.0
     */
    public WebImporterPipelineContext(ImporterPipelineContext ipc) {
        super(ipc.getCrawlerContext(), ipc.getDoc());
        BeanUtil.copyProperties(this, ipc);
    }

    public WebImporterPipelineContext(
            CrawlerContext crawler, CrawlDoc doc) {
        super(crawler, doc);
    }
}
