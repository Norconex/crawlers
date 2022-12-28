/* Copyright 2021 Norconex Inc.
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
package com.norconex.importer.handler;

import java.util.ArrayList;
import java.util.List;

import com.norconex.commons.lang.event.EventManager;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.filter.DocumentFilter;
import com.norconex.importer.parser.ParseState;

//TODO move to .impl package, or hide visibility?
public class HandlerContext {

    private final Doc doc;
    private final List<Doc> childDocs = new ArrayList<>();
    private final ParseState parseState;
    private final EventManager eventManager;

    private DocumentFilter rejectedBy;
    private final IncludeMatchResolver includeResolver =
            new IncludeMatchResolver();

    public HandlerContext(
            Doc doc,
            EventManager eventManager,
            ParseState parseState) {
        super();
        this.doc = doc;
        this.eventManager = eventManager;
        this.parseState = parseState;
    }
    public Doc getDoc() {
        return doc;
    }
    public List<Doc> getChildDocs() {
        return childDocs;
    }
    public ParseState getParseState() {
        return parseState;
    }
    public EventManager getEventManager() {
        return eventManager;
    }
    public boolean isRejected() {
        return rejectedBy != null;
    }
    public DocumentFilter getRejectedBy() {
        return rejectedBy;
    }
    public void setRejectedBy(DocumentFilter rejectedBy) {
        this.rejectedBy = rejectedBy;
    }
    public IncludeMatchResolver getIncludeResolver() {
        return includeResolver;
    }

    public static class IncludeMatchResolver {
        private boolean hasIncludes = false;
        private boolean atLeastOneIncludeMatch = false;
        public boolean hasIncludes() {
            return hasIncludes;
        }
        public void setHasIncludes(boolean hasIncludes) {
            this.hasIncludes = hasIncludes;
        }
        public boolean atLeastOneIncludeMatch() {
            return atLeastOneIncludeMatch;
        }
        public void setAtLeastOneIncludeMatch(boolean atLeastOneIncludeMatch) {
            this.atLeastOneIncludeMatch = atLeastOneIncludeMatch;
        }
        public boolean passes() {
            return !(hasIncludes && !atLeastOneIncludeMatch);
        }
    }
}
