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
package com.norconex.grid.core.util;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

/**
 * Wraps a task in code that changes the name of the thread running
 * the task for the duration of that task.
 */
public final class ThreadRenamer {

    private enum NameAction {
        SET, PREFIX, SUFFIX
    }

    private ThreadRenamer() {
    }

    public static Runnable suffix(String name, Runnable task) {
        return doNaming(name, task, NameAction.SUFFIX);
    }

    public static <T> Supplier<T> suffix(String name, Supplier<T> task) {
        return doNaming(name, task, NameAction.SUFFIX);
    }

    public static <T> Callable<T> suffix(String name, Callable<T> task) {
        return doNaming(name, task, NameAction.SUFFIX);
    }

    public static Runnable prefix(String name, Runnable task) {
        return doNaming(name, task, NameAction.PREFIX);
    }

    public static <T> Supplier<T> prefix(String name, Supplier<T> task) {
        return doNaming(name, task, NameAction.PREFIX);
    }

    public static <T> Callable<T> prefix(String name, Callable<T> task) {
        return doNaming(name, task, NameAction.PREFIX);
    }

    public static Runnable set(String name, Runnable task) {
        return doNaming(name, task, NameAction.SET);
    }

    public static <T> Supplier<T> set(String name, Supplier<T> task) {
        return doNaming(name, task, NameAction.SET);
    }

    public static <T> Callable<T> set(String name, Callable<T> task) {
        return doNaming(name, task, NameAction.SET);
    }

    //--- Private methods ------------------------------------------------------

    private static Runnable doNaming(
            String name, Runnable task, NameAction action) {
        return () -> {
            try {
                doNaming(name, Executors.callable(task), action).call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        };
    }

    private static <T> Supplier<T> doNaming(
            String name, Supplier<T> supplier, NameAction action) {
        Callable<T> callable = supplier::get;
        Callable<T> namedCallable = doNaming(name, callable, action);
        return () -> {
            try {
                return namedCallable.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        };
    }

    private static <T> Callable<T> doNaming(
            String name, Callable<T> task, NameAction action) {
        if (StringUtils.isBlank(name)) {
            return task;
        }

        return () -> {
            var current = Thread.currentThread();
            var originalName = current.getName();
            var newName = switch (action) {
                case SUFFIX -> originalName + "-" + name;
                case PREFIX -> name + " - " + originalName;
                default -> name;
            };
            current.setName(newName);
            try {
                return task.call();
            } finally {
                current.setName(originalName);
            }
        };
    }

}
