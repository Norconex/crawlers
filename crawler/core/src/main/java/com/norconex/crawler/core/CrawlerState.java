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
package com.norconex.crawler.core;

import com.norconex.crawler.core.grid.GridCache;

import lombok.Data;
import lombok.NonNull;

@Data
public class CrawlerState {

    public static final String KEY_CACHE = "CrawlerState";
    public static final String KEY_QUEUE_INITIALIZED = ".queueInitialized";
    public static final String KEY_RESUMING = KEY_CACHE + ".resuming";
    public static final String KEY_STOP_REQUESTED =
            KEY_CACHE + ".stopRequested";

    private GridCache<Boolean> cache;

    public void init(@NonNull CrawlerContext crawlerContext) {
        cache = crawlerContext.getGrid().storage().getCache(
                "CrawlerState", Boolean.class);
    }

    public boolean isResuming() {
        return getState(KEY_RESUMING);
    }

    public void setResuming(boolean resumed) {
        setState(KEY_RESUMING, resumed);
    }

    public boolean isQueueInitialized() {
        return getState(KEY_QUEUE_INITIALIZED);
    }

    public void setQueueInitialized(boolean queueInitialized) {
        setState(KEY_QUEUE_INITIALIZED, queueInitialized);
    }

    public boolean isStopRequested() {
        return getState(KEY_STOP_REQUESTED);
    }

    public void setStopRequested(boolean stopRequested) {
        setState(KEY_STOP_REQUESTED, stopRequested);
    }

    public void setState(String key, boolean value) {
        if (cache == null) {
            throw new IllegalStateException(
                    "CrawlerState must be initialized before usage.");
        }
        cache.put(key, value);
    }

    public boolean getState(String key) {
        if (cache == null) {
            throw new IllegalStateException(
                    "CrawlerState must be initialized before usage.");
        }
        return Boolean.TRUE.equals(cache.get(key));
    }

}
