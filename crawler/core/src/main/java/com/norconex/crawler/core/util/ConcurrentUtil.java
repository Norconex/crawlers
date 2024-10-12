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
package com.norconex.crawler.core.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class ConcurrentUtil {
    private ConcurrentUtil() {
    }

    /**
     * Blocks the current thread and waits until the future has completed.
     * Handles {@link InterruptedException} and will wrap it, along with
     * {@link ConcurrentException}.
     * Passing a <code>null</code> future has no effect and returns null
     * @param future the future to block
     * @return the future response, if any, or <code>null</code>
     */
    public static Object block(Future<?> future) {
        if (future == null) {
            return null;
        }
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConcurrentException("Thread was interrupted.", e);
        } catch (ExecutionException e) {
            throw new ConcurrentException("Task failed.", e);
        }
    }
}
