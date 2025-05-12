/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.session;

import static com.norconex.crawler.core.util.ExceptionSwallower.swallow;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.slf4j.MDC;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.util.ConfigUtil;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class CrawlContextDestroyer {

    private CrawlContextDestroyer() {
    }

    // closes associated resources
    static void destroy(CrawlContext ctx) {
        if (ctx == null) {
            LOG.info("No CrawlContext to destroy (null).");
            return;
        }

        // Defer shutdown
        swallow(() -> Optional.ofNullable(
                ctx.getCrawlConfig().getDeferredShutdownDuration())
                .filter(d -> d.toMillis() > 0)
                .ifPresent(d -> {
                    LOG.info("Deferred shutdown requested. Pausing for {} "
                            + "starting from this UTC moment: {}",
                            DurationFormatter.FULL.format(d),
                            LocalDateTime.now(ZoneOffset.UTC));
                    Sleeper.sleepMillis(d.toMillis());
                    LOG.info("Shutdown resumed.");
                }));

        ExceptionSwallower.close(ctx.getCommitterService());

        swallow(MDC::clear);
        ExceptionSwallower.close(ctx.getMetrics()::close);
        var tempDir = ConfigUtil.resolveTempDir(ctx.getCrawlConfig());
        swallow(() -> {
            if (tempDir != null) {
                FileUtil.delete(tempDir.toFile());
            }
        }, "Could not delete the temporary directory:" + tempDir);

        ctx.getGrid().close();
        swallow(ctx.getEventManager()::clearListeners);
    }
}
