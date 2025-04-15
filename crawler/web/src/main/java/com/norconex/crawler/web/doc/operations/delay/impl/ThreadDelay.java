/* Copyright 2010-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.delay.impl;

public class ThreadDelay extends AbstractDelay {

    private static final ThreadLocal<Long> THREAD_LAST_HIT_MILLIS = //NOSONAR
            ThreadLocal.withInitial(System::currentTimeMillis);

    @Override
    public void delay(long expectedDelayMillis, String url) {
        long lastHitMillis = THREAD_LAST_HIT_MILLIS.get();
        delay(expectedDelayMillis, lastHitMillis);
        THREAD_LAST_HIT_MILLIS.set(System.currentTimeMillis());
    }
}
