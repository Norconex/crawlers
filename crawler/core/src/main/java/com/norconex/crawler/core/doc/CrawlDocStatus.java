/* Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Reference processing status.
 */
@JsonAutoDetect(
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
//TODO maybe rename something along the lines of DocProcessingStatus?
public class CrawlDocStatus implements Serializable {

    private static final Map<String, CrawlDocStatus> STATUSES =
            new HashMap<>();

    //MAYBE refactor to facilitate JSON serialization without annotations?

    //TODO make default state UNKNOWN/UNPROCESSED, or equivalent that means
    // TBD/IN_PROGRESS????
    // Because NEW is misleading in the case where it exists in the cache but
    // no checksum defined... then it is nether new nor modified.
    private static final long serialVersionUID = 6542269270632505768L;

    public static final CrawlDocStatus NEW =
            new CrawlDocStatus("NEW");
    public static final CrawlDocStatus MODIFIED =
            new CrawlDocStatus("MODIFIED");
    public static final CrawlDocStatus UNMODIFIED =
            new CrawlDocStatus("UNMODIFIED");
    public static final CrawlDocStatus ERROR =
            new CrawlDocStatus("ERROR");
    public static final CrawlDocStatus REJECTED =
            new CrawlDocStatus("REJECTED");
    public static final CrawlDocStatus BAD_STATUS =
            new CrawlDocStatus("BAD_STATUS");
    public static final CrawlDocStatus DELETED =
            new CrawlDocStatus("DELETED");
    public static final CrawlDocStatus NOT_FOUND =
            new CrawlDocStatus("NOT_FOUND");
    /**
     * For collectors that support it, this state indicates a previously
     * crawled document is not yet ready to be re-crawled.  It may or may not
     * be re-crawled in the next crawl session (if ready).
         */
    public static final CrawlDocStatus PREMATURE =
            new CrawlDocStatus("PREMATURE");

    //TODO testing this... remove if not used:
    /**
     * Typically when a reference cannot be processed since it is
     * not supported by the collector or one of its configured
     * component.
         */
    public static final CrawlDocStatus UNSUPPORTED =
            new CrawlDocStatus("UNSUPPORTED");

    public static final CrawlDocStatus TOO_DEEP =
            new CrawlDocStatus("TOO_DEEP");

    @JsonProperty
    @JsonValue
    private final String state;

    /**
     * Constructor.
     * @param state state code
     */
    protected CrawlDocStatus(String state) {
        this.state = state;
        STATUSES.put(state, this);
    }

    /**
     * Returns whether a reference should be considered "good" (the
     * corresponding document is not in a "bad" state, such as being rejected
     * or produced an error.
     * This implementation will consider as good these reference statuses:
     * {@link #NEW}, {@link #MODIFIED}, {@link #UNMODIFIED},
     * and {@link #PREMATURE}.
     * This method can be overridden to provide different logic for a valid
     * reference.
     * @return <code>true</code> if status is valid.
     */
    public boolean isGoodState() {
        return isOneOf(NEW, MODIFIED, UNMODIFIED, PREMATURE);
    }

    /**
     * Null-safe version of {@link #isGoodState()}.  A <code>null</code>
     * state returns <code>false</code>.
     * @param state the state to test
     * @return <code>true</code> if status is valid.
     */
    public static boolean isGoodState(CrawlDocStatus state) {
        return state == null ? false : state.isGoodState();
    }

    /**
     * Returns whether a state indicates new or modified.
     * @return <code>true</code> if new or modified
     */
    public boolean isNewOrModified() {
        return isOneOf(NEW, MODIFIED);
    }

    /**
     * Returns whether a state indicate the document is to be skipped
     * ({@link #UNMODIFIED} or {@link #PREMATURE}).
     * @return <code>true</code> if skipped
     */
    public boolean isSkipped() {
        return isOneOf(UNMODIFIED, PREMATURE);
    }

    public boolean isOneOf(CrawlDocStatus... states) {
        if (ArrayUtils.isEmpty(states)) {
            return false;
        }
        for (CrawlDocStatus crawlState : states) {
            if (equals(crawlState)) {
                return true;
            }
        }
        return false;
    }

    @JsonCreator
    public static synchronized CrawlDocStatus valueOf(
            @JsonProperty("state") String state) {
        var refState = STATUSES.get(state);
        if (refState == null) {
            refState = new CrawlDocStatus(state);
        }
        return refState;
    }

    @Override
    public String toString() {
        return state;
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
