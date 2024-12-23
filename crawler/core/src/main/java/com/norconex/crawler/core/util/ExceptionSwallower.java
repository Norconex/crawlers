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

import java.io.Closeable;

import org.apache.commons.lang3.function.FailableRunnable;

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
     * are swallowed and a default message logged.
     * @param closeables one ore more resources to close
     */
    public static void close(Closeable... closeables) {
        if (closeables == null) {
            return;
        }
        for (Closeable closeable : closeables) {
            close(closeable, "Could not close resource.");
        }
    }

    /**
     * Ensures that any exceptions thrown while closing the supplied resource
     * are swallowed and a custom message logged.
     * @param closeable resource to close
     */
    public static void close(Closeable closeable, String msg) {
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
    public static void closeQuietly(Closeable... closeables) {
        if (closeables == null) {
            return;
        }
        for (Closeable closeable : closeables) {
            close(closeable, null);
        }
    }

    //    "Could not close resource."
}
