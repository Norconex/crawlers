/* Copyright 2020-2024 Norconex Inc.
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
package com.norconex.committer.core;

import java.io.File;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.io.CachedStreamFactory;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Holds objects defined outside a committer but useful or required for the
 * committer execution.
 */
@ToString(doNotUseGetters = true)
@EqualsAndHashCode(doNotUseGetters = true)
public final class CommitterContext {

    private EventManager eventManager;
    private Path workDir;
    private CachedStreamFactory streamFactory;

    private CommitterContext() {
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    /**
     * Gets a unique working directory for a committer (if one is needed).
     * @return working directory (never {@code null})
     */
    public Path getWorkDir() {
        return workDir;
    }

    public CachedStreamFactory getStreamFactory() {
        return streamFactory;
    }

    public CommitterContext withEventManager(EventManager eventManager) {
        return CommitterContext.builder()
                .setEventManager(eventManager)
                .setWorkDir(workDir)
                .setStreamFactory(streamFactory)
                .build();
    }

    public CommitterContext withWorkdir(Path workDir) {
        return CommitterContext.builder()
                .setEventManager(eventManager)
                .setWorkDir(workDir)
                .setStreamFactory(streamFactory)
                .build();
    }

    public CommitterContext withStreamFactory(
            CachedStreamFactory streamFactory) {
        return CommitterContext.builder()
                .setEventManager(eventManager)
                .setWorkDir(workDir)
                .setStreamFactory(streamFactory)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CommitterContext ctx = new CommitterContext();

        private Builder() {
        }

        public Builder setWorkDir(Path workDir) {
            ctx.workDir = workDir;
            return this;
        }

        public Builder setEventManager(EventManager eventManager) {
            ctx.eventManager = eventManager;
            return this;
        }

        public Builder setStreamFactory(CachedStreamFactory streamFactory) {
            ctx.streamFactory = streamFactory;
            return this;
        }

        public CommitterContext build() {
            if (ctx.workDir == null) {
                ctx.workDir = new File(
                        FileUtils.getTempDirectory(),
                        "committer-" + TimeIdGenerator.next()).toPath();
            }
            if (ctx.eventManager == null) {
                ctx.eventManager = new EventManager();
            }
            if (ctx.streamFactory == null) {
                ctx.streamFactory = new CachedStreamFactory();
            }
            return ctx;
        }
    }
}
