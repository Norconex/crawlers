/* Copyright 2017-2024 Norconex Inc.
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
package com.norconex.crawler.web.mocks;

import java.util.Iterator;

import org.apache.commons.collections4.iterators.ObjectArrayIterator;

import com.norconex.crawler.core.pipelines.queue.ReferencesProvider;

import lombok.EqualsAndHashCode;

// Used in crawler full validation.
@EqualsAndHashCode
public class MockStartURLsProvider implements ReferencesProvider {

    @Override
    public Iterator<String> provideReferences() {
        return new ObjectArrayIterator<>(
                "http://www.provided1.com",
                "http://www.provided2.com",
                "http://www.provided3.com");
    }
}
