/* Copyright 2022-2024 Norconex Inc.
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

import static com.norconex.commons.lang.file.FileUtil.toSafeFileName;
import static org.apache.commons.lang3.StringUtils.stripToEmpty;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.zip.GZIPOutputStream;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes documents to filesystem.
 * Not part of public API.
 */
@Slf4j
@EqualsAndHashCode
@ToString
class FSDocWriterHandler<T> implements AutoCloseable {
    private final String fileBaseName;
    private int writeCount;
    // start file numbering at 1
    private int fileNumber = 1;
    private File file;
    private T docWriter;
    private Writer writer = null;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final AbstractFSCommitter<T,
            ? extends BaseFSCommitterConfig> committer;

    FSDocWriterHandler(
            AbstractFSCommitter<T, ? extends BaseFSCommitterConfig> committer,
            String fileBaseName
    ) {
        this.committer = committer;
        this.fileBaseName = fileBaseName;
    }

    synchronized T withDocWriter() throws IOException {
        var docPerFile = committer.getConfiguration().getDocsPerFile();
        var docPerFileReached = docPerFile > 0 && writeCount == docPerFile;

        if (docPerFileReached) {
            close();
        }

        // invocation count is zero or max reached, we need a new file.
        if (writeCount == 0) {
            file = new File(
                    committer.getResolvedDirectory().toFile(),
                    buildFileName()
            );
            LOG.info("Creating file: {}", file);
            if (committer.getConfiguration().isCompress()) {
                writer = new OutputStreamWriter(
                        new GZIPOutputStream(
                                new FileOutputStream(file), true
                        )
                );
            } else {
                writer = new FileWriter(file);
            }
            docWriter = committer.createDocWriter(writer);
            fileNumber++;
        }
        writeCount++;
        return docWriter;
    }

    private String buildFileName() {
        var fileName = stripToEmpty(
                toSafeFileName(
                        committer.getConfiguration().getFileNamePrefix()
                )
        )
                + fileBaseName + stripToEmpty(
                        toSafeFileName(
                                committer.getConfiguration().getFileNameSuffix()
                        )
                )
                + "_" + fileNumber + "." + committer.getFileExtension();
        if (committer.getConfiguration().isCompress()) {
            fileName += ".gz";
        }
        return fileName;
    }

    @Override
    public synchronized void close() throws IOException {
        writeCount = 0;
        try {
            if (writer != null) {
                committer.closeDocWriter(docWriter);
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
        file = null;
        writer = null;
    }
}
