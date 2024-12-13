/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.pipelines;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.DocResolutionStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Checksum stage utility methods.
 */
@Slf4j
public final class ChecksumStageUtil {

    private ChecksumStageUtil() {
    }

    public static boolean resolveMetaChecksum(
            String newChecksum, CrawlDoc doc) {
        return resolveChecksum(true, newChecksum, doc);
    }

    public static boolean resolveDocumentChecksum(
            String newChecksum, CrawlDoc doc) {
        return resolveChecksum(false, newChecksum, doc);
    }

    // return false if checksum is rejected/unmodified
    private static boolean resolveChecksum(
            boolean isMeta,
            String newChecksum,
            CrawlDoc doc) {
        var docContext = doc.getDocContext();

        // Set new checksum on crawlData + metadata
        String type;
        if (isMeta) {
            docContext.setMetaChecksum(newChecksum);
            type = "metadata";
        } else {
            docContext.setContentChecksum(newChecksum);
            type = "document";
        }

        // Get old checksum from cache
        var cachedDocInfo = doc.getCachedDocContext();

        // if there was nothing in cache, or what is in cache is a deleted
        // doc, consider as new.
        if (cachedDocInfo == null
                || DocResolutionStatus.DELETED.isOneOf(cachedDocInfo.getState())) {
            LOG.debug(
                    "ACCEPTED {} checkum (new): Reference={}",
                    type, docContext.getReference());

            // Prevent not having status when finalizing document on embedded
            // docs (which otherwise do not have a status.
            // But if already has a status, keep it.
            if (docContext.getState() == null) {
                docContext.setState(DocResolutionStatus.NEW);
            }
            return true;
        }

        String oldChecksum = null;
        if (isMeta) {
            oldChecksum = cachedDocInfo.getMetaChecksum();
        } else {
            oldChecksum = cachedDocInfo.getContentChecksum();
        }

        // Compare checksums
        if (StringUtils.isNotBlank(newChecksum)
                && Objects.equals(newChecksum, oldChecksum)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        "REJECTED {} checkum (unmodified): Reference={}",
                        type, docContext.getReference());
            }
            docContext.setState(DocResolutionStatus.UNMODIFIED);

            //            var s = new StringBuilder();
            //            if (subject != null) {
            //                s.append(subject.getClass().getSimpleName() + " - ");
            //            }
            //            s.append("Checksum=" + StringUtils.abbreviate(newChecksum, 200));

            //            ctx.fire(CrawlerEvent.builder()
            //                    .name(CrawlerEvent.REJECTED_UNMODIFIED)
            //                    .source(ctx.getCrawler())
            //                    .crawlDocRecord(ctx.getDocRecord())
            //                    .subject(subject)
            //                    .message(s.toString())
            //                    .build());
            return false;
        }

        docContext.setState(DocResolutionStatus.MODIFIED);
        LOG.debug(
                "ACCEPTED {} checksum (modified): Reference={}",
                type, docContext.getReference());
        return true;
    }
}
