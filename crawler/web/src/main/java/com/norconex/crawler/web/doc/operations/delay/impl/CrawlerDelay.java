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
package com.norconex.crawler.web.doc.operations.delay.impl;

import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.commons.lang.Sleeper;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * It is assumed there will be one instance of this class per crawler defined.
 */
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class CrawlerDelay extends AbstractDelay {

    private MutableLong lastHitEpochMillis = new MutableLong(-1);
    private boolean sleeping = false;

    @Override
    public void delay(long expectedDelayMillis, String url) {
        if (expectedDelayMillis <= 0) {
            return;
        }
        try {
            synchronized (lastHitEpochMillis) {
                while (sleeping) {
                    Sleeper.sleepMillis(Math.min(
                            TINY_SLEEP_MS, expectedDelayMillis));
                }
                sleeping = true;
            }
            delay(expectedDelayMillis, lastHitEpochMillis.longValue());
            lastHitEpochMillis.setValue(System.currentTimeMillis());
        } finally {
            sleeping = false;
        }
    }
}
