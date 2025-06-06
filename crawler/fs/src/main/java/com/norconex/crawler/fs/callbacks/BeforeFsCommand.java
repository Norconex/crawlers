/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.fs.callbacks;

import java.util.function.Consumer;

import com.norconex.crawler.core.session.CrawlContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Web crawler-specific initialization before the crawler starts.
 */
@Slf4j
public class BeforeFsCommand implements Consumer<CrawlContext> {

    @Override
    public void accept(CrawlContext crawlContext) {
        var cfg = crawlContext.getCrawlConfig();
        LOG.info("""

                Resuming:         %s

                Enabled features:

                Metadata:
                  Checksummer:    %s
                  Deduplication:  %s
                Document:
                  Checksummer:    %s
                  Deduplication:  %s
                """.formatted(
                yn(crawlContext.isResumedSession()),
                yn(cfg.getMetadataChecksummer() != null),
                yn(cfg.isMetadataDeduplicate()
                        && cfg.getMetadataChecksummer() != null),
                yn(cfg.getDocumentChecksummer() != null),
                yn(cfg.isDocumentDeduplicate()
                        && cfg.getDocumentChecksummer() != null)));
    }

    private static String yn(boolean value) {
        return value ? "Yes" : "No";
    }
}
