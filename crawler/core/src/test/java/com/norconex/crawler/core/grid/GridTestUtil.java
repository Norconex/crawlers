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
package com.norconex.crawler.core.grid;

import org.apache.ignite.Ignition;

import com.norconex.commons.lang.Sleeper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class GridTestUtil {
    private GridTestUtil() {
    }

    public static void waitForGridShutdown() {
        if (isIgniteRunning()) {
            LOG.info("Ignite still running, stopping it.");
            Ignition.stopAll(true);
        }
        var cnt = 0;
        do {
            Sleeper.sleepMillis(500);
            if (cnt >= 10) {
                LOG.error("Ignite did not appear to shutdown.");
                break;
            }
            cnt++;
        } while (isIgniteRunning());
    }

    private static boolean isIgniteRunning() {
        try {
            Ignition.ignite();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

}
