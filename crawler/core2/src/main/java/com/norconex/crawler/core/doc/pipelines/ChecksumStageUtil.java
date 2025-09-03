/* Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.ledger.ProcessingOutcome;

import lombok.extern.slf4j.Slf4j;

/**
 * Checksum stage utility methods.
 */
@Slf4j
public final class ChecksumStageUtil {

    private ChecksumStageUtil() {
    }

    public static boolean resolveMetaChecksum(
            String newChecksum, CrawlDocContext docCtx) {
        return resolveChecksum(true, newChecksum, docCtx);
    }

    public static boolean resolveDocumentChecksum(
            String newChecksum, CrawlDocContext docCtx) {
        return resolveChecksum(false, newChecksum, docCtx);
    }

    // return false if checksum is rejected/unmodified
    private static boolean resolveChecksum(
            boolean isMeta,
            String newChecksum,
            CrawlDocContext docCtx) {
        var currentCrawlEntry = docCtx.getCurrentCrawlEntry();

        // Set new checksum on crawlData + metadata
        String type;
        if (isMeta) {
            currentCrawlEntry.setMetaChecksum(newChecksum);
            type = "metadata";
        } else {
            currentCrawlEntry.setContentChecksum(newChecksum);
            type = "document";
        }

        // Get old checksum from cache
        var prevCrawlEntry = docCtx.getPreviousCrawlEntry();

        // if there was nothing in cache, or what is in cache is a deleted
        // doc, consider as new.
        if (prevCrawlEntry == null || ProcessingOutcome.DELETED
                .isOneOf(prevCrawlEntry.getProcessingOutcome())) {
            LOG.debug("ACCEPTED {} checkum (new): Reference={}",
                    type, docCtx.getReference());

            // Prevent not having status when finalizing document on embedded
            // docs (which otherwise do not have a status.
            // But if already has a status, keep it.
            if (currentCrawlEntry.getProcessingOutcome() == null) {
                currentCrawlEntry.setProcessingOutcome(ProcessingOutcome.NEW);
            }
            return true;
        }

        String oldChecksum = null;
        if (isMeta) {
            oldChecksum = prevCrawlEntry.getMetaChecksum();
        } else {
            oldChecksum = prevCrawlEntry.getContentChecksum();
        }

        // Compare checksums
        if (StringUtils.isNotBlank(newChecksum)
                && Objects.equals(newChecksum, oldChecksum)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED {} checkum (unmodified): Reference={}",
                        type, docCtx.getReference());
            }
            currentCrawlEntry
                    .setProcessingOutcome(ProcessingOutcome.UNMODIFIED);
            return false;
        }

        currentCrawlEntry.setProcessingOutcome(ProcessingOutcome.MODIFIED);
        LOG.debug("ACCEPTED {} checksum (modified): Reference={}",
                type, docCtx.getReference());
        return true;
    }
}
