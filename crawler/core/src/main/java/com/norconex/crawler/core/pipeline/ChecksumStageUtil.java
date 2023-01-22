/* Copyright 2014-2022 Norconex Inc.
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

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.crawler.core.doc.CrawlDocState;

/**
 * Checksum stage utility methods.
 */
public final class ChecksumStageUtil {

    private static final Logger LOG =
            LoggerFactory.getLogger(ChecksumStageUtil.class);

    private ChecksumStageUtil() {
    }


    public static boolean resolveMetaChecksum(
            String newChecksum, DocumentPipelineContext ctx, Object subject) {
        return resolveChecksum(true, newChecksum, ctx, subject);
    }
    public static boolean resolveDocumentChecksum(
            String newChecksum, DocumentPipelineContext ctx, Object subject) {
        return resolveChecksum(false, newChecksum, ctx, subject);
    }


    // return false if checksum is rejected/unmodified
    private static boolean resolveChecksum(boolean isMeta, String newChecksum,
            DocumentPipelineContext ctx, Object subject) {
        var docInfo = ctx.getDocRecord();

        // Set new checksum on crawlData + metadata
        String type;
        if (isMeta) {
            docInfo.setMetaChecksum(newChecksum);
            type = "metadata";
        } else {
            docInfo.setContentChecksum(newChecksum);
            type = "document";
        }

        // Get old checksum from cache
        var cachedDocInfo = ctx.getCachedDocRecord();

        // if there was nothing in cache, or what is in cache is a deleted
        // doc, consider as new.
        if (cachedDocInfo == null
                || CrawlDocState.DELETED.isOneOf(cachedDocInfo.getState())) {
            LOG.debug("ACCEPTED {} checkum (new): Reference={}",
                    type, docInfo.getReference());

            // Prevent not having status when finalizing document on embedded docs
            // (which otherwise do not have a status.
            // But if already has a status, keep it.
            if (docInfo.getState() == null) {
                docInfo.setState(CrawlDocState.NEW);
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
                LOG.debug("REJECTED {} checkum (unmodified): Reference={}",
                        type, docInfo.getReference());
            }
            docInfo.setState(CrawlDocState.UNMODIFIED);

            var s = new StringBuilder();
            if (subject != null) {
                s.append(subject.getClass().getSimpleName() + " - ");
            }
            s.append("Checksum=" + StringUtils.abbreviate(newChecksum, 200));

            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_UNMODIFIED)
                    .source(ctx.getCrawler())
                    .crawlDocRecord(ctx.getDocRecord())
                    .subject(subject)
                    .message(s.toString())
                    .build());
            return false;
        }

        docInfo.setState(CrawlDocState.MODIFIED);
        LOG.debug("ACCEPTED {} checksum (modified): Reference={}",
                type, docInfo.getReference());
        return true;
    }
}
