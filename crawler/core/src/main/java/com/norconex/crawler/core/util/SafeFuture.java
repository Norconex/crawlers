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
package com.norconex.crawler.core.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Future} that gracefully handles exceptions from the
 * <code>get</code> methods and returns a runtime {@link CompletionException}
 * instead. Can also wrap existing future instances to make them "safe".
 * @param <T> the type of the future return value
 */
public class SafeFuture<T> implements Future<T> {

    private final Future<T> delegate;
    private final Runnable onCompletion;

    public SafeFuture() {
        this(new CompletableFuture<>(), null);
    }

    /**
     * Constructor with optional task executed just before one of the
     * future <code>get</code> methods return. If no <code>get</code>
     * method is invoked, this optional task never runs.
     * @param onCompletion optional task
     */
    public SafeFuture(Runnable onCompletion) {
        this(new CompletableFuture<>(), onCompletion);
    }

    private SafeFuture(Future<T> delegate, Runnable onCompletion) {
        this.delegate = delegate;
        this.onCompletion = onCompletion;
    }

    /**
     * Decorates an existing future instance to make it "safe".
     * @param delegate the original future instance
     * @return a safe future
     */
    public static <T> SafeFuture<T> wrap(Future<T> delegate) {
        return new SafeFuture<>(delegate, null);
    }

    /**
     * Decorates an existing future instance to make it "safe".
     * @param delegate the original future instance
     * @param onCompletion optional task executed just before "get" returns
     * @return a safe future
     */
    public static <T> SafeFuture<T> wrap(
            Future<T> delegate, Runnable onCompletion) {
        return new SafeFuture<>(delegate, onCompletion);
    }

    public Future<T> unwrap() {
        return delegate;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return delegate.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return delegate.isCancelled();
    }

    @Override
    public boolean isDone() {
        return delegate.isDone();
    }

    @Override
    public T get() {
        try {
            return ConcurrentUtil.get(delegate);
        } finally {
            if (onCompletion != null) {
                onCompletion.run();
            }
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit) {
        try {
            return ConcurrentUtil.get(delegate, timeout, unit);
        } finally {
            if (onCompletion != null) {
                onCompletion.run();
            }
        }
    }
}
