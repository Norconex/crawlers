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
package com.norconex.grid.core;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TestGridContext extends BaseGridContext {

    private final Map<String, Object> context = new ConcurrentHashMap<>();

    public TestGridContext(Path workDir) {
        super(workDir);
    }

    public Object get(String key) {
        return context.get(key);
    }

    public void set(String key, Object obj) {
        context.put(key, obj);
    }

    public void clear() {
        context.clear();
    }

}
