/* Copyright 2025-2026 Norconex Inc.
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

import java.util.Optional;

import com.norconex.crawler.core.cluster.CacheMap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Wrapper around an existing cluster string cache, offering generic attribute
 * setters and getters for convenience. Whether and how the attributes are
 * persisted is dependent on the underlying cache implementation.
 */
@RequiredArgsConstructor
public class CrawlAttributes {
    /**
     * The underlying cache.
     */
    @Getter
    private final CacheMap<String> cache;

    //--- String ---------------------------------------------------------------

    /**
     * Sets an attribute as a string.
     * @param key attribute key
     * @param value attribute value
     */
    public void setString(String key, String value) {
        cache.put(key, value);
    }

    /**
     * Atomically sets an attribute as a string if it is not
     * already set. This is a cluster-safe operation that prevents race
     * conditions in distributed environments.
     * @param key attribute key
     * @param value attribute value to set if key is absent
     * @return true if the value was set (key was absent), false if the
     *         key already existed
     */
    public boolean setStringIfAbsent(String key, String value) {
        return cache.putIfAbsent(key, value) == null;
    }

    /**
     * Gets an attribute as a string.
     * @param key attribute key
     * @return value attribute value
     */
    public Optional<String> getString(String key) {
        return cache.get(key);
    }

    //--- Boolean --------------------------------------------------------------

    /**
     * Sets an attribute as a boolean.
     * @param key attribute key
     * @param value attribute value
     */
    public void setBoolean(String key, boolean value) {
        cache.put(key, Boolean.toString(value));
    }

    /**
     * Atomically sets an attribute as a boolean if it is not
     * already set. This is a cluster-safe operation that prevents race
     * conditions in distributed environments.
     * @param key attribute key
     * @param value attribute value to set if key is absent
     * @return true if the value was set (key was absent), false if the
     *         key already existed
     */
    public boolean setBooleanIfAbsent(String key, boolean value) {
        return cache.putIfAbsent(key, Boolean.toString(value)) == null;
    }

    /**
     * Gets an attribute as a boolean.
     * @param key attribute key
     * @return value attribute value
     */
    public boolean getBoolean(String key) {
        return cache.get(key).map(Boolean::parseBoolean).orElse(false);
    }

    //--- Integer --------------------------------------------------------------

    /**
     * Sets an attribute as an integer.
     * @param key attribute key
     * @param value attribute value
     */
    public void setInteger(String key, int value) {
        cache.put(key, Integer.toString(value));
    }

    /**
     * Atomically sets an attribute as an integer if it is not
     * already set. This is a cluster-safe operation that prevents race
     * conditions in distributed environments.
     * @param key attribute key
     * @param value attribute value to set if key is absent
     * @return true if the value was set (key was absent), false if the
     *         key already existed
     */
    public boolean setIntegerIfAbsent(String key, int value) {
        return cache.putIfAbsent(key, Integer.toString(value)) == null;
    }

    /**
     * Gets an attribute as an integer. If no value is set, zero is returned.
     * @param key attribute key
     * @return value attribute value, or zero
     */
    public int getInteger(String key) {
        return cache.get(key).map(Integer::parseInt).orElse(0);
    }
}
