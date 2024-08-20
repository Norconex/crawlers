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
package com.norconex.crawler.core.fetch;

import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.doc.CrawlDocState;

import lombok.NonNull;

/**
 * Utility methods for fetching.
 */
public final class FetchUtil {

    private FetchUtil() {}

    public static boolean shouldContinueOnBadStatus(
            @NonNull Crawler crawler,
            CrawlDocState originalCrawlDocState,
            @NonNull FetchDirective fetchDirective) {
        // Note: a disabled directive should never get here,
        // and when both are enabled, DOCUMENT always comes after METADATA.
        var metaSupport = crawler.getConfiguration().getMetadataFetchSupport();
        var docSupport = crawler.getConfiguration().getDocumentFetchSupport();

        //--- HEAD ---
        if (FetchDirective.METADATA.is(fetchDirective)) {
            // if directive is required, we end it here.
            if (FetchDirectiveSupport.REQUIRED.is(metaSupport)) {
                return false;
            }
            // if head is optional and there is a GET, we continue
            return FetchDirectiveSupport.OPTIONAL.is(metaSupport)
                    && FetchDirectiveSupport.isEnabled(docSupport);

        //--- GET ---
        }
        if (FetchDirective.DOCUMENT.is(fetchDirective)) {
            // if directive is required, we end it here.
            if (FetchDirectiveSupport.REQUIRED.is(docSupport)) {
                return false;
            }
            // if directive is optional and HEAD was enabled and successful,
            // we continue
            return FetchDirectiveSupport.OPTIONAL.is(docSupport)
                    && FetchDirectiveSupport.isEnabled(metaSupport)
                    && CrawlDocState.isGoodState(originalCrawlDocState);
        }

        // At this point it would imply the directive for which we are asking
        // is disabled. It should not be possible to get a bad status
        // if disabled, so something is wrong, and we do not continue.
        return false;
    }
}
