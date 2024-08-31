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
package com.norconex.committer.core.batch.queue.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Iterator;

import org.apache.commons.collections4.iterators.BoundedIterator;
import org.apache.commons.collections4.iterators.TransformIterator;

import com.norconex.committer.core.CommitterRequest;
import com.norconex.commons.lang.collection.CountingIterator;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;

/**
 * Encapsulates a batch (full or partial) and offers ways to iterate through
 * it and delete it. Not thread-safe.  Meant to be used on a directory
 * whose content is handled by a single thread (this one).
 */
class FsBatch implements Iterable<CommitterRequest> {

    private CachedStreamFactory streamFactory;
    private final Path dir;
    private final long max;

    /**
     * Creates a new file system batch.
     * @param streamFactory stream factory
     * @param dir directory where requests are queued
     * @param max Maximum number of committer request to iterate through. A
     *     value lower or equal to zero matches all requests found in directory
     */
    FsBatch(CachedStreamFactory streamFactory, Path dir, long max) {
        this.streamFactory = streamFactory;
        this.dir = dir;
        this.max = max;
    }

    @Override
    public CountingIterator<CommitterRequest> iterator() {
        try {
            return new CountingIterator<>(
                    new TransformIterator<>(
                            zipIterator(), this::loadCommitterRequest
                    )
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path getDir() {
        return dir;
    }

    public void delete() throws IOException {
        var it = zipIterator();
        while (it.hasNext()) {
            FileUtil.delete(it.next().toFile());
        }
    }

    /**
     * Move this batch to the supplied directory, appending this
     * batch directory name to the target directory.
     * @param toDir where to copy this batch.
     * @throws IOException problem copying batch.
     */
    public void move(Path toDir) throws IOException {
        var targetDir = toDir.resolve(dir.getFileName());
        var it = zipIterator();
        while (it.hasNext()) {
            FileUtil.moveFileToDir(it.next().toFile(), targetDir.toFile());
        }
    }

    private Iterator<Path> zipIterator() throws IOException {
        var it = FSQueueUtil.findZipFiles(dir).iterator();
        if (max > 0) {
            it = new BoundedIterator<>(it, 0, max);
        }
        return it;
    }

    private CommitterRequest loadCommitterRequest(Path file) {
        try {
            return FSQueueUtil.fromZipFile(file, streamFactory);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
