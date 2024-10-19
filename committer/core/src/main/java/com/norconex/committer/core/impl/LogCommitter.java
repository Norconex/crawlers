/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.committer.core.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.time.StopWatch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.committer.core.AbstractCommitter;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.impl.LogCommitterConfig.LogLevel;
import com.norconex.commons.lang.Slf4jUtil;
import com.norconex.commons.lang.map.Properties;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * <b>WARNING: Not intended for production use.</b>
 * </p>
 * <p>
 * A Committer that logs all data associated with every document, added or
 * removed, to the application logs, or the console (STDOUT/STDERR). Default
 * uses application logger with INFO log level.
 * </p>
 * <p>
 * This Committer can be useful for troubleshooting.  Given how much
 * information this could represent, it is recommended
 * you do not use in a production environment. At a minimum, if you are
 * logging to file, make sure to rotate/clean the logs regularly.
 * </p>
 */
@Slf4j
@Data
public class LogCommitter extends AbstractCommitter<LogCommitterConfig> {

    private static final int LOG_TIME_BATCH_SIZE = 100;

    @JsonIgnore
    private long addCount = 0;
    @JsonIgnore
    private long removeCount = 0;

    private final LogCommitterConfig configuration = new LogCommitterConfig();

    @Override
    public LogCommitterConfig getConfiguration() {
        return configuration;
    }

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private final StopWatch watch = new StopWatch();

    @Override
    protected void doInit() throws CommitterException {
        watch.reset();
        watch.start();
    }

    @Override
    protected void doUpsert(UpsertRequest upsertRequest)
            throws CommitterException {
        var b = new StringBuilder();
        b.append("\n=== DOCUMENT UPSERTED ================================\n");

        stringifyRefAndMeta(
                b, upsertRequest.getReference(), upsertRequest.getMetadata());

        if (!configuration.isIgnoreContent()) {
            b.append("\n--- Content ---------------------------------------\n");
            try {
                b.append(
                        IOUtils.toString(
                                upsertRequest.getContent(), UTF_8))
                        .append('\n');
            } catch (IOException e) {
                b.append(ExceptionUtils.getStackTrace(e));
            }
        }
        log(b.toString());

        addCount++;
        if (addCount % LOG_TIME_BATCH_SIZE == 0) {
            LOG.info("{} upsert logged in: {}", addCount, watch);
        }
    }

    @Override
    protected void doDelete(DeleteRequest deleteRequest)
            throws CommitterException {
        var b = new StringBuilder();
        b.append("\n=== DOCUMENT DELETED =================================\n");
        stringifyRefAndMeta(
                b, deleteRequest.getReference(), deleteRequest.getMetadata());
        log(b.toString());

        removeCount++;
        if (removeCount % LOG_TIME_BATCH_SIZE == 0) {
            LOG.info("{} delete logged in {}", removeCount, watch);
        }
    }

    @Override
    protected void doClose() throws CommitterException {
        if (watch.isStarted()) {
            watch.stop();
        }
        LOG.info("{} additions committed.", addCount);
        LOG.info("{} deletions committed.", removeCount);
        LOG.info("Total elapsed time: {}", watch);
    }

    @Override
    protected void doClean() throws CommitterException {
        // NOOP
    }

    private void stringifyRefAndMeta(
            StringBuilder b, String reference, Properties metadata) {
        b.append("REFERENCE = ").append(reference).append('\n');
        if (metadata != null) {
            b.append("\n--- Metadata: -------------------------------------\n");
            for (Entry<String, List<String>> en : metadata.entrySet()) {
                if (configuration.getFieldMatcher().getPattern() == null
                        || configuration.getFieldMatcher().matches(
                                en.getKey())) {
                    for (String val : en.getValue()) {
                        b.append(en.getKey()).append(" = ")
                                .append(val).append('\n');
                    }
                }

            }
        }
    }

    private void log(String txt) {
        var lvl = Optional.ofNullable(configuration.getLogLevel())
                .orElse(LogLevel.INFO);
        if (LogLevel.STDERR == lvl) {
            System.err.println(txt); //NOSONAR
        } else if (LogLevel.STDOUT == lvl) {
            System.out.println(txt); //NOSONAR
        } else {
            Slf4jUtil.log(LOG, lvl.toString(), txt);
        }
    }
}
