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

package com.norconex.crawler.core.store;

import com.norconex.crawler.core.doc.CrawlDocContext;

// Adds a few extra field types for testing
class TestObject extends CrawlDocContext {

    private static final long serialVersionUID = 1L;
    private int count;
    private boolean valid;

    TestObject() {
    }

    TestObject(
            String reference, int count, String checksum,
            String parentReference) {
        super(reference);
        this.count = count;
        setContentChecksum(checksum);
        setParentRootReference(parentReference);
    }

    int getCount() {
        return count;
    }

    void setCount(int count) {
        this.count = count;
    }

    boolean isValid() {
        return valid;
    }

    void setValid(boolean valid) {
        this.valid = valid;
    }
}
