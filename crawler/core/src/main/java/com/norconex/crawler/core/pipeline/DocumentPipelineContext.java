/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.crawler.core.pipeline;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocRecord;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Pipeline context for document processing.
 */
@EqualsAndHashCode
@ToString
public class DocumentPipelineContext extends AbstractPipelineContext {

    private CrawlDoc document;

    public DocumentPipelineContext(
            Crawler crawler,
            CrawlDoc document) {
        super(Objects.requireNonNull(crawler, "'crawler' must not be null."));
        this.document = Objects.requireNonNull(
                document, "'document' must not be null.");
    }

    public CrawlDoc getDocument() {
        return document;
    }

    public CrawlDocRecord getDocRecord() {
        return document.getDocRecord();
    }


    /**
     * Gets cached crawl data.
     * @return cached crawl data
     */
    public CrawlDocRecord getCachedDocRecord() {
        return document.getCachedDocRecord();
    }

    public CachedInputStream getContent() {
        return getDocument().getInputStream();
    }

    public Reader getContentReader() {
        return new InputStreamReader(
                getDocument().getInputStream(), StandardCharsets.UTF_8);
    }
}
