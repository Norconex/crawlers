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
package com.norconex.crawler.core.grid.impl.ignite;

import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;

import com.norconex.crawler.core.CrawlerContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class IgniteGridUtil {

    private IgniteGridUtil() {
    }

    public static CrawlerContext getCrawlerContext() {
        return getInitService().getContext();
    }

    //    public static CrawlerConfig getCrawlerConfig() {
    //        return getInitService().getCrawlerConfig();
    //    }

    private static IgniteGridCrawlerContextService getInitService() {
        try {
            var ignite = Ignition.localIgnite();
            return ignite.services().serviceProxy(
                    IgniteGridKeys.CONTEXT_SERVICE, IgniteGridCrawlerContextService.class,
                    false);
        } catch (IgniteException e) {
            LOG.error("Could not obtain crawler context from Ignite session. "
                    + "Was it initialized and stored in session beforehand?");
            throw e;
        }
    }
}
