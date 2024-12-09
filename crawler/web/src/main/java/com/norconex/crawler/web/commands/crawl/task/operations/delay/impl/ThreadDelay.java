/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.commands.crawl.task.operations.delay.impl;

public class ThreadDelay extends AbstractDelay {

    private static final ThreadLocal<Long> THREAD_LAST_HIT_NANOS = //NOSONAR
            ThreadLocal.withInitial(System::nanoTime);

    @Override
    public void delay(long expectedDelayNanos, String url) {
        long lastHitNanos = THREAD_LAST_HIT_NANOS.get();
        delay(expectedDelayNanos, lastHitNanos);
        THREAD_LAST_HIT_NANOS.set(System.nanoTime());
    }
}
