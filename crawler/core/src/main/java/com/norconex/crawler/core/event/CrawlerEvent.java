/* Copyright 2018-2024 Norconex Inc.
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
package com.norconex.crawler.core.event;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.event.Event;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.tasks.TaskContext;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * A crawler event.
 */
@Data
@Setter(value = AccessLevel.NONE)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CrawlerEvent extends Event {

    //TODO rename some to be "CRAWLTASK_..."

    private static final long serialVersionUID = 1L;

    /**
     * The crawler began its initialization.
     */
    public static final String CRAWLER_INIT_BEGIN = "CRAWLER_INIT_BEGIN";
    /**
     * The crawler has been initialized.
     */
    public static final String CRAWLER_INIT_END = "CRAWLER_INIT_END";

    /**
     * The crawler is about to begin crawling.
     */
    public static final String CRAWLER_CRAWL_BEGIN = "CRAWLER_CRAWL_BEGIN";
    /**
     * The crawler completed crawling execution normally
     * (without being stopped). This event is triggered before the crawler
     * resources are released.
     */
    public static final String CRAWLER_CRAWL_END = "CRAWLER_CRAWL_END";

    /**
     * The crawler just started a new crawling thread.
     */
    public static final String CRAWLER_RUN_THREAD_BEGIN =
            "CRAWLER_RUN_THREAD_BEGIN";
    /**
     * The crawler completed execution of a crawling thread.
     */
    public static final String CRAWLER_RUN_THREAD_END =
            "CRAWLER_RUN_THREAD_END";

    /**
     * Issued when a request to stop the crawler has been received.
     */
    public static final String CRAWLER_STOP_BEGIN = "CRAWLER_STOP_BEGIN";
    /**
     * Issued when a request to stop the crawler has been fully executed
     * (crawler stopped).
     */
    public static final String CRAWLER_STOP_END = "CRAWLER_STOP_END";

    /**
     * Issued when the crawler is done processing and is about to shut down.
     */
    public static final String CRAWLER_SHUTDOWN_BEGIN =
            "CRAWLER_SHUTDOWN_BEGIN";
    /**
     * Issued when the crawler is done processing and has shut down.
     */
    public static final String CRAWLER_SHUTDOWN_END = "CRAWLER_SHUTDOWN_END";

    public static final String CRAWLER_CLEAN_BEGIN = "CRAWLER_CLEAN_BEGIN";
    public static final String CRAWLER_CLEAN_END = "CRAWLER_CLEAN_END";

    public static final String CRAWLER_STORE_EXPORT_BEGIN =
            "CRAWLER_STORE_EXPORT_BEGIN";
    public static final String CRAWLER_STORE_EXPORT_END =
            "CRAWLER_STORE_EXPORT_END";
    public static final String CRAWLER_STORE_IMPORT_BEGIN =
            "CRAWLER_STORE_IMPORT_BEGIN";
    public static final String CRAWLER_STORE_IMPORT_END =
            "CRAWLER_STORE_IMPORT_END";

    public static final String TASK_CONTEXT_INIT_BEGIN =
            "TASK_CONTEX_INIT_BEGIN";
    public static final String TASK_CONTEXT_INIT_END = "TASK_CONTEXT_INIT_END";
    public static final String TASK_RUN_BEGIN = "TASK_RUN_BEGIN";
    public static final String TASK_RUN_END = "TASK_RUN_END";
    public static final String TASK_CONTEXT_SHUTDOWN_BEGIN =
            "TASK_CONTEXT_SHUTDOWN_BEGIN";
    public static final String TASK_CONTEXT_SHUTDOWN_END =
            "TASK_CONTEXT_SHUTDOWN_END";

    public static final String CRAWLER_ERROR = "CRAWLER_ERROR";

    /**
     * A document was rejected by a filters.
     */
    public static final String REJECTED_FILTER = "REJECTED_FILTER";
    /**
     * A document was rejected as it was not modified since
     * last time it was crawled.
     */
    public static final String REJECTED_UNMODIFIED = "REJECTED_UNMODIFIED";
    /**
     * A document was rejected since another document with a different
     * reference was already processed with the same digital signature (
     * checksum).
         */
    public static final String REJECTED_DUPLICATE = "REJECTED_DUPLICATE";
    /**
     * A document could not be re-crawled because it is not yet ready to be
     * re-crawled.
     */
    public static final String REJECTED_PREMATURE = "REJECTED_PREMATURE";
    /**
     * A document was rejected because it could not be found (e.g., no longer
     * exists at a given location).
     */
    public static final String REJECTED_NOTFOUND = "REJECTED_NOTFOUND";
    /**
     * A document was rejected because the status obtained when trying
     * to obtain it was not accepted (e.g., 500 HTTP error code).
     */
    public static final String REJECTED_BAD_STATUS = "REJECTED_BAD_STATUS";
    /**
     * A document was rejected because it is located too deep.
     */
    public static final String REJECTED_TOO_DEEP = "REJECTED_TOO_DEEP";
    /**
     * A document was rejected by the Importer module.
     */
    public static final String REJECTED_IMPORT = "REJECTED_IMPORT";
    /**
     * A document was rejected because an error occurred when processing it.
     */
    public static final String REJECTED_ERROR = "REJECTED_ERROR";
    /**
     * A document pre-import processor was executed properly.
     */
    public static final String DOCUMENT_PREIMPORTED = "DOCUMENT_PREIMPORTED";
    /**
     * A document was imported.
     */
    public static final String DOCUMENT_IMPORTED = "DOCUMENT_IMPORTED";
    /**
     * A document post-import processor was executed properly.
     */
    public static final String DOCUMENT_POSTIMPORTED = "DOCUMENT_POSTIMPORTED";

    /**
     * A document metadata fields were successfully retrieved.
     */
    public static final String DOCUMENT_METADATA_FETCHED =
            "DOCUMENT_METADATA_FETCHED";
    /**
     * A document was successfully retrieved for processing.
     */
    public static final String DOCUMENT_FETCHED = "DOCUMENT_FETCHED";

    /**
     * A document reference was queued in the data store for processing.
     */
    public static final String DOCUMENT_QUEUED = "DOCUMENT_QUEUED";

    /**
     * A document was processed (successfully or not).
     */
    public static final String DOCUMENT_PROCESSED = "DOCUMENT_PROCESSED";

    //    /**
    //     * A document was saved.
    //     */
    //    public static final String DOCUMENT_SAVED = "DOCUMENT_SAVED";

    /**
     * Gets the crawl data holding contextual information about the
     * crawled reference.  CRAWLER_* events will return a <code>null</code>
     * crawl data.
     */
    private final CrawlDocContext docContext;
    private final Object subject;
    //TODO keep a reference to actual document?

    /**
     * Gets the subject. That is often the entity being the target
     * of the event (as opposed to the source).
     * @return the subject
     */
    public Object getSubject() {
        return subject;
    }

    @Override
    public TaskContext getSource() {
        return (TaskContext) super.getSource();
    }

    public boolean isCrawlerShutdown() {
        return is(CRAWLER_CRAWL_END, CRAWLER_STOP_END);
    }

    @Override
    public String toString() {
        var b = new StringBuilder();
        // always print the reference if we have it
        if (docContext != null) {
            b.append(docContext.getReference()).append(" - ");
        }

        // print the first value set in that order: message, subject, source
        if (StringUtils.isNotBlank(getMessage())) {
            b.append(getMessage());
        } else if (subject != null) {
            b.append(subject.toString());
        } else {
            b.append(Objects.toString(source));
        }

        return b.toString();
    }
}
