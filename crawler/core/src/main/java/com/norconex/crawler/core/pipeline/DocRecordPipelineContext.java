/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.crawler.core.pipeline;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.doc.CrawlDocRecord;

import lombok.Data;
import lombok.ToString;

/**
 * Hold necessary objects to a specific pipeline execution over a
 * {@link CrawlDocRecord}.
 */
@Data
@ToString(doNotUseGetters = true)
public class DocRecordPipelineContext extends AbstractPipelineContext {

    private final CrawlDocRecord docRecord;

    public DocRecordPipelineContext(Crawler crawler, CrawlDocRecord docRecord) {
        super(crawler);
        this.docRecord = docRecord;
    }

    public CrawlDocRecord getDocRecord() {
        return docRecord;
    }


}
