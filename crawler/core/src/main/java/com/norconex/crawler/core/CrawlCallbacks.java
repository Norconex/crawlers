/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core;

import java.util.function.BiConsumer;

import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.crawl.CrawlCommand;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.importer.doc.Doc;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class CrawlCallbacks {

    /**
     * Convenience interface providing a default implementation of
     * execute that ignores all commands but {@link CrawlCommand} and
     * offers an overloaded {@code accept} method with a single argument.
     */
    public interface CrawlCommandCallback
            extends BiConsumer<CrawlSession, Class<? extends Command>> {
        @Override
        default void accept(
                CrawlSession session, Class<? extends Command> commandClass) {
            if (CrawlCommand.class.isAssignableFrom(commandClass)) {
                accept(session);
            }
        }

        void accept(CrawlSession t);
    }

    /**
     * Invoked after a command is initialized, but before it gets executed.
     * Rely on the supplied command class to know which command.
     * @see CrawlCommandCallback
     */
    BiConsumer<CrawlSession, Class<? extends Command>> beforeCommand;
    /**
     * Invoked after a command has been executed, but before resources are
     * closed.
     * Rely on the supplied command class to know which command.
     * @see CrawlCommandCallback
     */
    BiConsumer<CrawlSession, Class<? extends Command>> afterCommand;

    //MAYBE: are those used? Should they be?
    // Add those that are missing to ReferencesProcessor
    BiConsumer<CrawlSession, Doc> beforeDocumentProcessing;
    BiConsumer<CrawlSession, Doc> afterDocumentProcessing;

    //MAYBE: need those, or we can replace beforeDocumentFinalizing
    // (the only one used) with after processing?
    BiConsumer<CrawlSession, Doc> beforeDocumentFinalizing;
    BiConsumer<CrawlSession, Doc> afterDocumentFinalizing;

}
