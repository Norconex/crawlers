/* Copyright 2023-2024 Norconex Inc.
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

import java.io.File;

import com.norconex.crawler.core.doc.CrawlDocLedgerEntry;
import com.norconex.importer.doc.DocContext;

import lombok.Data;
import lombok.NonNull;

/**
 * A path being crawled holding relevant crawl information.
 */
@Data
public class FsCrawlDocContext extends CrawlDocLedgerEntry {

    private static final long serialVersionUID = 1L;

    private boolean file;
    private boolean folder;

    public FsCrawlDocContext() {
    }

    public FsCrawlDocContext(String reference) {
        super(reference);
    }

    public FsCrawlDocContext(String reference, int depth) {
        super(reference);
        setDepth(depth);
    }

    /**
     * Copy constructor.
     * @param docRecord document record to copy
     */
    public FsCrawlDocContext(DocContext docRecord) {
        super(docRecord);
    }

    @Override
    public void setReference(@NonNull String reference) {
        // No protocol specified: we assume local file, and we get
        // the absolute version.
        // TODO really? do we want to force having absolute links?
        // or if only for start references, move logic there?
        if (reference.contains("://") || reference.matches("^\\w+:\\\\.*")) {
            super.setReference(reference);
        } else {
            super.setReference(new File(reference).getAbsolutePath());
        }
    }
}
