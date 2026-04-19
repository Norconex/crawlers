/* Copyright 2023-2026 Norconex Inc.
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
package com.norconex.crawler.fs.doc;

import com.norconex.crawler.fs.ledger.FsCrawlEntry;

// TODO: Delete this file. Replaced by FsCrawlEntry.
/** @deprecated Use {@link FsCrawlEntry} instead. */
@Deprecated(since = "4.0.0", forRemoval = true)
public class FsCrawlDocContext extends FsCrawlEntry {

    private static final long serialVersionUID = 1L;

    public FsCrawlDocContext() {
    }

    public FsCrawlDocContext(String reference) {
        super(reference);
    }

    public FsCrawlDocContext(String reference, int depth) {
        super(reference, depth);
    }
}
