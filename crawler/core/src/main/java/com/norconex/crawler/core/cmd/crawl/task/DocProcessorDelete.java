/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.task;

import com.norconex.crawler.core.doc.DocResolutionStatus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class DocProcessorDelete {

    private DocProcessorDelete() {
    }

    static void execute(DocProcessorContext ctx) {
        LOG.debug("Deleting reference: {}", ctx.doc().getReference());
        ctx.doc().getDocContext().setState(DocResolutionStatus.DELETED);
        // Event triggered by service
        ctx.crawlerContext().getCommitterService().delete(ctx.doc());
        DocProcessorFinalize.execute(ctx);
    }
}