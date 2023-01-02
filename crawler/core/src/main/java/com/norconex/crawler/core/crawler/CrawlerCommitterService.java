/* Copyright 2020-2022 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.crawler.core.doc.CrawlDoc;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper around multiple Committers so they can all be handled as one.
 */
@Slf4j
//TODO Move to a committer package, or to the committer-core project?
// second option likely the best
@EqualsAndHashCode
public class CrawlerCommitterService {

    private final List<Committer> committers = new ArrayList<>();
    private final Crawler crawler;

    public CrawlerCommitterService(Crawler crawler) {
        CollectionUtil.setAll(
                committers, crawler.getCrawlerConfig().getCommitters());
        this.crawler = crawler;

    }

    public boolean isEmpty() {
        return committers.isEmpty();
    }

    public void init(CommitterContext baseContext) {
        var idx = new MutableInt();
        executeAll("init", c -> {
            var ctx = baseContext.withWorkdir(
                    baseContext.getWorkDir().resolve(idx.toString()));
            idx.increment();
            c.init(ctx);
        });
    }

    /**
     * Updates or inserts a document using all accepting committers.
     * @param doc the document to upsert
     * @return committers having accepted/upserted the document
     */
    public List<Committer> upsert(CrawlDoc doc) {
        List<Committer> actuals = new ArrayList<>();
        if (!committers.isEmpty()) {
            executeAll("upsert", c -> {
                var req = toUpserRequest(doc);
                if (c.accept(req)) {
                    actuals.add(c);
                    c.upsert(req);
                    doc.getInputStream().rewind();
                }
            });
        }
        fireCommitterRequestEvent(
                CrawlerEvent.DOCUMENT_COMMITTED_UPSERT, actuals, doc);

        return actuals;
    }

    /**
     * Delete a document operation using all accepting committers.
     * @param doc the document to delete
     * @return committers having accepted/deleted the document
     */
    public List<Committer> delete(CrawlDoc doc) {
        List<Committer> actuals = new ArrayList<>();
        if (!committers.isEmpty()) {
            executeAll("delete", c -> {
                var req = toDeleteRequest(doc);
                if (c.accept(req)) {
                    actuals.add(c);
                    c.delete(req);
                    // no doc content rewind necessary
                }
            });
        }
        fireCommitterRequestEvent(
                CrawlerEvent.DOCUMENT_COMMITTED_DELETE, actuals, doc);
        return actuals;
    }

    public void close() {
        executeAll("close", Committer::close);
    }

    public void clean() {
        executeAll("clean", Committer::clean);
    }

    private void executeAll(String operation, CommitterConsumer consumer) {
        List<String> failures = new ArrayList<>();
        for (Committer committer : committers) {
            try {
                consumer.accept(committer);
            } catch (CommitterException e) {
                LOG.error("Could not execute \"{}\" on committer: {}",
                        operation, committer, e);
                failures.add(committer.getClass().getSimpleName());
            }
        }
        if (!failures.isEmpty()) {
            throw new CrawlerException(
                    "Could not execute \"" + operation + "\" on "
                    + failures.size() + " committer(s): \""
                    + StringUtils.join(failures, ", ")
                    + "\". Check the logs for more details.");
        }
    }

    // invoked for each committer to avoid tempering
    private UpsertRequest toUpserRequest(CrawlDoc doc) {
        return new UpsertRequest(
                doc.getReference(),
                doc.getMetadata(),
                doc.getInputStream());
    }
    // invoked for each committer to avoid tempering
    private DeleteRequest toDeleteRequest(CrawlDoc doc) {
        return new DeleteRequest(doc.getReference(), doc.getMetadata());
    }

    private void fireCommitterRequestEvent(
            String eventName, List<Committer> targets, CrawlDoc doc) {
        var msg = "Committers: " + (
                targets.isEmpty()
                ? "none"
                : targets.stream().map(c -> c.getClass().getSimpleName())
                        .collect(Collectors.joining(",")));
        crawler.getEventManager().fire(CrawlerEvent.builder()
                .name(eventName)
                .source(crawler)
                .crawlDocRecord(doc.getDocInfo())
                .subject(targets)
                .message(msg)
                .build());
    }


    @Override
    public String toString() {
        return CrawlerCommitterService.class.getSimpleName() + '[' +
                committers.stream().map(c -> c.getClass().getSimpleName())
                        .collect(Collectors.joining(",")) + ']';
    }

    @FunctionalInterface
    private interface CommitterConsumer {
        void accept(Committer c) throws CommitterException;
    }
}
