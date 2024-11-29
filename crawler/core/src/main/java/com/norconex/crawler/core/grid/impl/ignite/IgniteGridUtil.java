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
import org.apache.ignite.internal.IgniteKernal;
import org.apache.ignite.lang.IgniteFuture;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.GridException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class IgniteGridUtil {

    private IgniteGridUtil() {
    }

    public static CrawlerContext getCrawlerContext() {
        return getInitService().getContext();
    }

    public static Object block(IgniteFuture<?> igniteFuture) {
        try {
            return igniteFuture.get();
        } catch (IgniteException e) {
            if (!isIgniteRunning()) {
                LOG.warn("Could not wait for Ignite future to return due to "
                        + "Ignite grid no longer running.");
                return null;
            }
            LOG.error("Clould not block Ignite future.", e);
            throw new GridException("Clould not block Ignite future.", e);
        }
    }

    private static IgniteGridCrawlerContextService getInitService() {
        try {
            var ignite = Ignition.localIgnite();
            return ignite.services().serviceProxy(
                    IgniteGridKeys.CONTEXT_SERVICE,
                    IgniteGridCrawlerContextService.class,
                    false);
        } catch (IgniteException e) {
            LOG.error("Could not obtain crawler context from Ignite session. "
                    + "Was it initialized and stored in session beforehand?");
            throw e;
        }
    }

    private static boolean isIgniteRunning() {
        try {
            var ignite = Ignition.ignite();
            return !((IgniteKernal) ignite).isStopping();
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
