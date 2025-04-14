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

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

public class NamingExecutor implements Executor {
    private final Executor delegate;
    private final String baseName;
    private final AtomicInteger counter = new AtomicInteger(0);

    public NamingExecutor(Executor delegate, String baseName) {
        this.delegate = delegate;
        this.baseName = baseName;
    }

    @Override
    public void execute(Runnable task) {
        var taskId = counter.getAndIncrement();
        delegate.execute(() -> {
            var current = Thread.currentThread();
            var originalName = current.getName();
            current.setName(baseName + "-" + taskId);
            try {
                task.run();
            } finally {
                current.setName(originalName);
            }
        });
    }
}
