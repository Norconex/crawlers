/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.committer.core.fs;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.commons.lang3.time.DateFormatUtils;

import com.norconex.committer.core.AbstractCommitter;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * Base class for committers writing to the local file system.
 * </p>
 *
 * <h3>XML configuration usage:</h3>
 * <p>
 * The following are configuration options inherited by subclasses:
 * </p>
 * {@nx.xml #options
 *   <directory>(path where to save the files)</directory>
 *   <docsPerFile>(max number of docs per file)</docsPerFile>
 *   <compress>[false|true]</compress>
 *   <splitUpsertDelete>[false|true]</splitUpsertDelete>
 *   <fileNamePrefix>(optional prefix to created file names)</fileNamePrefix>
 *   <fileNameSuffix>(optional suffix to created file names)</fileNameSuffix>
 *   {@nx.include com.norconex.committer.core.AbstractCommitter@nx.xml.usage}
 * }
 *
 * @param <T> type of file serializer
 * @param <C> type of configuration object
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public abstract class AbstractFSCommitter<T, C extends BaseFSCommitterConfig>
        extends AbstractCommitter<C> {

    // These will share the same instance if not split.
    @Getter(value = AccessLevel.NONE) @Setter(value = AccessLevel.NONE)
    private FSDocWriterHandler<T> upsertHandler;
    @Getter(value = AccessLevel.NONE) @Setter(value = AccessLevel.NONE)
    private FSDocWriterHandler<T> deleteHandler;
    @Getter
    private Path directory;

    @Override
    protected void doInit() throws CommitterException {
        if (getConfiguration().getDirectory() == null) {
            directory = getCommitterContext().getWorkDir();
        } else {
            directory = getConfiguration().getDirectory();
        }

        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new CommitterException("Could not create directory: "
                    + directory.toAbsolutePath(), e);
        }

        var fileBaseName = DateFormatUtils.format(
                System.currentTimeMillis(), "yyyy-MM-dd'T'hh-mm-ss-SSS");
        if (getConfiguration().isSplitUpsertDelete()) {
            upsertHandler =
                    new FSDocWriterHandler<>(this, "upsert-" + fileBaseName);
            deleteHandler =
                    new FSDocWriterHandler<>(this, "delete-" + fileBaseName);
        } else {
            // when using same file for both upsert and delete, share instance.
            upsertHandler = new FSDocWriterHandler<>(this, fileBaseName);
            deleteHandler = upsertHandler;
        }
    }
    @Override
    protected synchronized void doUpsert(UpsertRequest upsertRequest)
            throws CommitterException {
        try {
            writeUpsert(upsertHandler.withDocWriter(), upsertRequest);
        } catch (IOException e) {
            throw new CommitterException("Could not write upsert request for: "
                    + upsertRequest.getReference());
        }
    }
    @Override
    protected synchronized void doDelete(DeleteRequest deleteRequest)
            throws CommitterException {
        try {
            writeDelete(deleteHandler.withDocWriter(), deleteRequest);
        } catch (IOException e) {
            throw new CommitterException("Could not write delete request for: "
                    + deleteRequest.getReference());
        }
    }
    @Override
    protected void doClose() throws CommitterException {
        try {
            if (upsertHandler != null) {
                upsertHandler.close();
            }
            if (deleteHandler != null
                    && !Objects.equals(deleteHandler, upsertHandler)) {
                deleteHandler.close();
            }
        } catch (IOException e) {
            throw new CommitterException("Could not close file writer.", e);
        }
    }

    @Override
    protected void doClean() throws CommitterException {
        // NOOP, no internal state is kept.
        // We do not clean previously committed files.
    }

    protected abstract String getFileExtension();
    protected abstract T createDocWriter(Writer writer) throws IOException;
    protected abstract void writeUpsert(
            T docWriter, UpsertRequest upsertRequest) throws IOException;
    protected abstract void writeDelete(
            T docWriter, DeleteRequest deleteRequest) throws IOException;
    protected abstract void closeDocWriter(T docWriter)
            throws IOException;
}
