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
package com.norconex.crawler.core2.util;

import org.apache.commons.lang3.function.FailableRunnable;

import com.norconex.crawler.core.CrawlerException;

import lombok.extern.slf4j.Slf4j;

//MAYBE: move somewhere more generic
@Slf4j
public final class ExceptionSwallower {
    private ExceptionSwallower() {
    }

    /**
     * Ensures that any exceptions thrown while executing are swallowed
     * and a default message logged.
     * @param runnable the code to execute
     */
    public static void swallow(FailableRunnable<Exception> runnable) {
        swallow(runnable, "An exception was swallowed.");
    }

    /**
     * Ensures that any exceptions thrown while executing are swallowed
     * and a custom message logged.
     * @param runnable the code to execute
     * @param msg message to log when there is an exception
     */
    public static void swallow(
            FailableRunnable<Exception> runnable, String msg) {
        if (runnable == null) {
            return;
        }
        try {
            runnable.run();
        } catch (Exception e) {
            if (msg != null) {
                LOG.error(msg, e);
            }
        }
    }

    /**
     * Ensures that any exceptions thrown while executing are swallowed
     * and no messages are logged about the exception.
     * @param runnable the code to execute
     */
    public static void swallowQuietly(FailableRunnable<Exception> runnable) {
        swallow(runnable, null);
    }

    /**
     * Ensures that any exceptions thrown while closing the supplied resource
     * are swallowed and a default message logged. {@code null} arguments
     * are ignored.
     * @param closeables one ore more resources to close
     */
    public static void close(AutoCloseable... closeables) {
        if (closeables == null) {
            return;
        }
        for (AutoCloseable closeable : closeables) {
            if (closeable == null)
                continue;
            var resourceName = closeable.getClass().getName();
            try {
                closeable.close();
                LOG.info("Successfully closed resource: {}", resourceName);
            } catch (Exception e) {
                LOG.error("Failed to close resource: {}", resourceName, e);
                // For critical resources, escalate or rethrow
                if (isCriticalResource(closeable)) {
                    throw new CrawlerException(
                            "Critical resource failed to close: "
                                    + resourceName,
                            e);
                }
            }
        }
    }

    /**
     * Determines if a resource is critical (e.g., cluster or cache manager).
     */
    private static boolean isCriticalResource(AutoCloseable closeable) {
        var name = closeable.getClass().getSimpleName().toLowerCase();
        return name.contains("cluster") || name.contains("cachemanager");
    }

    /**
     * Ensures that any exceptions thrown while closing the supplied resource
     * are swallowed and a custom message logged.
     * @param closeable resource to close
     * @param msg message to display in case of failure to close
     */
    public static void close(AutoCloseable closeable, String msg) {
        if (closeable == null) {
            return;
        }
        swallow(closeable::close, msg);
    }

    /**
     * Ensures that any exceptions thrown while closing the supplied resource
     * are swallowed and no messages are logged about the exception.
     * @param closeables one or more resources to close
     */
    public static void closeQuietly(AutoCloseable... closeables) {
        if (closeables == null) {
            return;
        }
        for (AutoCloseable closeable : closeables) {
            close(closeable, null);
        }
    }

    /**
     * Runs the given runnable, clearing the thread's interrupt flag before execution
     * and restoring it after if it was set. Useful for shutdown/close logic that must
     * not be interrupted.
     * @param runnable the code to execute
     */
    public static void runWithInterruptClear(Runnable runnable) {
        var wasInterrupted = Thread.interrupted(); // clears the flag
        try {
            runnable.run();
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Runs the given FailableRunnable, clearing the thread's interrupt flag before execution
     * and restoring it after if it was set. Useful for shutdown/close logic that must
     * not be interrupted. Any thrown exception is propagated.
     * @param runnable the code to execute
     * @throws Exception if the runnable throws 
     */
    public static void runWithInterruptClearEx(
            FailableRunnable<Exception> runnable) throws Exception {
        var wasInterrupted = Thread.interrupted();
        try {
            runnable.run();
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    //    "Could not close resource."
}
