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

import static java.lang.Math.ceil;
import static java.lang.Math.log;
import static java.lang.Math.log10;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.leftPad;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.MutableObject;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.batch.BatchConsumer;
import com.norconex.committer.core.batch.queue.CommitterQueue;
import com.norconex.committer.core.batch.queue.CommitterQueueException;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.exec.RetriableException;
import com.norconex.commons.lang.exec.Retrier;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * File System queue. Queues committer requests as zip files and deletes
 * successfully committed zip files.
 * </p>
 * <p>
 * The top-level queue directory is the one defined in the
 * {@link CommitterContext} initialization argument or the system
 * temporary directory if {@code null}. A "queue" sub-folder will be
 * created for queued requests, while an "error" one will also be created
 * for failed batches.
 * </p>
 *
 * <h2>Handling Failures</h2>
 * <p>
 * Before moving a failed batch to the "error" folder you have the opportunity
 * to have this queue tell the Committer to retry the batch.  This can be
 * particularly useful if you experience issues such as:
 * </p>
 * <ul>
 *   <li>The target repository momentarily failed to respond.</li>
 *   <li>A specific document was rejected by the target repository.</li>
 *   <li>The batch was too big for the repository to handle.</li>
 *   <li>Etc.</li>
 * </ul>
 * <p>
 * In addition to specifying how many time to retry and how long to wait
 * between each attempts, you can also break the batch size into smaller
 * chunks.  The "splitBatch" setting offers the following options:
 * </p>
 * <ul>
 *   <li><b>OFF</b> - Default. The failing batch is not split.</li>
 *   <li><b>HALF</b> - The batch is split in half at each failure
 *       (after maxRetries is reached), up until batches are of size 1
 *       (committing documents individually).</li>
 *   <li><b>ONE</b> - Documents from the failing batch are sent one by one.</li>
 * </ul>
 * <p>
 * When "splitBatch" is not OFF and used in combination with a non-zero
 * "maxRetry", the later then represents how many times to retry each new
 * smaller batches created.
 * </p>
 */
@EqualsAndHashCode
@ToString
@Slf4j
public class FsQueue implements CommitterQueue, Configurable<FsQueueConfig> {

    public enum SplitBatch {
        OFF, HALF, ONE
    }

    // If not supplied in CommitterContext, defaults to OS temp dir
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Path queueDir;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Path errorDir;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private AtomicLong batchCount = new AtomicLong();
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private BatchConsumer batchConsumer;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private CachedStreamFactory streamFactory;

    // directory currently being written into (up to batch size).
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Path activeDir;

    private Retrier retrier = new Retrier(0);

    @Getter
    private final FsQueueConfig configuration = new FsQueueConfig();

    @Override
    public void init(
            CommitterContext committerContext,
            @NonNull BatchConsumer batchConsumer)
            throws CommitterQueueException {

        this.batchConsumer = Objects.requireNonNull(
                batchConsumer,
                "'batchConsumer' must not be null.");

        LOG.info("Initializing file system Committer queue...");

        // Workdir:
        var workDir = Optional.ofNullable(committerContext.getWorkDir())
                .orElseGet(() -> Paths.get(FileUtils.getTempDirectoryPath()));
        streamFactory = committerContext.getStreamFactory();

        retrier = new Retrier();
        retrier.setMaxRetries(
                configuration.getOnCommitFailure().getMaxRetries());
        retrier.setRetryDelay(
                configuration.getOnCommitFailure().getRetryDelay());

        LOG.info(
                "Committer working directory: {}",
                workDir.toAbsolutePath());
        queueDir = workDir.resolve("queue");
        errorDir = workDir.resolve("error");
        try {
            FileUtils.forceMkdir(workDir.toFile());
            FileUtils.forceMkdir(queueDir.toFile());
            FileUtils.forceMkdir(errorDir.toFile());
        } catch (IOException e) {
            throw new CommitterQueueException(
                    "Could not create committer queue directory: "
                            + workDir.toAbsolutePath());
        }

        if (configuration.isCommitLeftoversOnInit()) {
            // Resume by first processing existing batches not yet committed
            // from previous execution.
            // "false" by default since we do not want to commit leftovers
            // when doing initialization for a "clean" operation.
            LOG.info("Committing any leftovers...");
            var cnt = consumeRemainingBatches();
            if (cnt == 0) {
                LOG.info("No leftovers.");
            } else {
                LOG.info("{} leftovers committed.", cnt);
            }
        }

        // MAYBE: when resuming a previous run, do we care to count files in
        // directories so we initialize the batch counter properly
        // to honor maxPerFolder?

        // Start for real
        activeDir = createActiveDir();

        LOG.info("File system Committer queue initialized.");
    }

    public BatchConsumer getBatchConsumer() {
        return batchConsumer;
    }

    @Override
    public void queue(CommitterRequest request)
            throws CommitterQueueException {

        var fullBatchDir = new MutableObject<Path>();

        var file = createQueueFile(request, fullBatchDir);

        try {
            FSQueueUtil.toZipFile(request, file);
        } catch (IOException e) {
            throw new CommitterQueueException(
                    "Could not queue request for "
                            + request.getReference() + " at "
                            + file.toAbsolutePath(),
                    e);
        }

        if (fullBatchDir.getValue() != null) {
            consumeBatchDirectory(fullBatchDir.getValue());
        }
    }

    // batch split(0) -> try(1) -> try(2) -> split(1) -> try(1) -> try(2) -> ...
    private int consumeBatchDirectory(Path dir) throws CommitterQueueException {
        try {
            return consumeSplitableBatchDirectory(dir);
        } catch (IOException e) {
            throw new CommitterQueueException(
                    "Could not read files to commit "
                            + "from directory " + dir.toAbsolutePath());
        }
    }

    private int consumeSplitableBatchDirectory(Path dir)
            throws CommitterQueueException, IOException {

        var totalConsumed = 0;
        var attemptDocConsumed = Math.max(1, configuration.getBatchSize());
        var batch = new FsBatch(streamFactory, dir, -1);
        var batchHadFailures = false;
        var batchRanSuccessfully = false;
        while (!FSQueueUtil.isEmpty(dir)) {
            try {
                var numConsumed = 0;
                batchRanSuccessfully = false;
                while ((numConsumed = consumeRetriableBatch(batch)) > 0) {
                    batchRanSuccessfully = true;
                    totalConsumed += numConsumed;
                }
            } catch (CommitterQueueException e) {
                batchHadFailures = true;
                attemptDocConsumed = reduceBatchSize(attemptDocConsumed);
                if (attemptDocConsumed == -1) {
                    moveUnrecoverableBatchError(batch, e);
                    break;
                }
                batch = new FsBatch(streamFactory, dir, attemptDocConsumed);
            }
        }

        if (batchHadFailures && batchRanSuccessfully) {
            LOG.info("Batch successfully recovered: {}", dir.toAbsolutePath());
        }

        // If an exception is thrown before reaching the following delete call,
        // it's OK since we do not want to delete a batch folder if there
        // were non-ignored exceptions in it.
        // In theory all files should be deleted or moved, but we call delete
        // here in case the folder itself remains.
        try {
            FileUtil.delete(dir.toFile());
        } catch (IOException e) {
            throw new CommitterQueueException(
                    "Could not delete consumed committer "
                            + "batch located at " + dir.toAbsolutePath(),
                    e);
        }

        return totalConsumed;
    }

    private int consumeRetriableBatch(FsBatch batch)
            throws CommitterQueueException {
        try {
            return retrier.execute(() -> {
                var it = batch.iterator();
                if (it.hasNext()) {
                    batchConsumer.consume(it);
                    // batch was consumed OK, delete it.
                    batch.delete();
                }
                return it.getCount();
            });
        } catch (RetriableException e) {
            throw new CommitterQueueException(
                    "Could not consume batch. Number of attempts: "
                            + (retrier.getMaxRetries() + 1),
                    e);
        }
    }

    private void moveUnrecoverableBatchError(
            FsBatch batch, Exception e) throws CommitterQueueException {
        try {
            batch.move(errorDir);
        } catch (IOException e1) {
            throw new CommitterQueueException(
                    "Could not process one or more files form committer "
                            + "batch located at "
                            + batch.getDir().toAbsolutePath()
                            + " and could not copy it under "
                            + errorDir.toAbsolutePath(),
                    e);
        }
        var msg = "Could not process one or more files form committer batch "
                + "located at " + batch.getDir().toAbsolutePath()
                + ". Moved them to error directory: "
                + errorDir.toAbsolutePath();
        if (!configuration.getOnCommitFailure().isIgnoreErrors()) {
            throw new CommitterQueueException(msg, e);
        }
        LOG.error(msg, e);
    }

    private int reduceBatchSize(int lastTriedSize) {
        // we do not got smaller than one, so we call it quit (-1)
        if (lastTriedSize <= 1) {
            return -1;
        }

        var sb = ofNullable(configuration.getOnCommitFailure().getSplitBatch())
                .orElse(SplitBatch.OFF);
        return switch (sb) {
            case HALF -> {
                var newMaxSize = (lastTriedSize + 1) / 2;
                LOG.error(
                        "Could not process batch of max size {}. Trying "
                                + "again with max size {}...",
                        lastTriedSize,
                        newMaxSize);
                yield newMaxSize;
            }
            case ONE -> {
                LOG.error(
                        "Could not process batch of max size {}. Trying "
                                + "again one by one...",
                        lastTriedSize);
                yield 1;
            }
            default -> -1;
        };
    }

    @Override
    public void close() throws CommitterQueueException {
        // specifying parent dir will process all that's left there.
        if (queueDir != null && Files.exists(queueDir)) {
            consumeRemainingBatches();
        }
    }

    private int consumeRemainingBatches() throws CommitterQueueException {
        // Process all batch dirs one by one:
        var cnt = 0;
        try (var directory =
                Files.newDirectoryStream(queueDir, Files::isDirectory)) {
            for (Path d : directory) {
                cnt += consumeBatchDirectory(d);
            }
        } catch (IOException e) {
            throw new CommitterQueueException(
                    "Could not consume "
                            + "remaining batches at "
                            + queueDir.toAbsolutePath(),
                    e);
        }
        return cnt;
    }

    @Override
    public void clean() throws CommitterQueueException {
        if (queueDir == null) {
            LOG.error("Queue directory not found. Nothing not clean.");
            return;
        }

        // move one level higher before deleting to get queue/active/errors
        try {
            FileUtil.delete(queueDir.getParent().toFile());
        } catch (IOException e) {
            throw new CommitterQueueException(
                    "Could not clean queue "
                            + "directory located at "
                            + queueDir.getParent().toAbsolutePath(),
                    e);
        }
    }

    private Path createActiveDir() {
        return queueDir.resolve("batch-" + TimeIdGenerator.next());
    }

    private synchronized Path createQueueFile(
            CommitterRequest req, MutableObject<Path> consumeBatchDir)
            throws CommitterQueueException {
        try {
            // If starting a new batch, create the folder for it
            if (batchCount.get() == configuration.getBatchSize()) {
                consumeBatchDir.setValue(activeDir);
                batchCount.set(0);
                activeDir = createActiveDir();
                Files.createDirectories(activeDir);
            }
        } catch (IOException e) {
            throw new CommitterQueueException(
                    "Could not create batch directory: "
                            + activeDir.toAbsolutePath());
        }

        var file = activeDir.resolve(
                filePath(batchCount.get())
                        + (req instanceof DeleteRequest ? "-delete" : "-upsert")
                        + FSQueueUtil.EXT);
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new CommitterQueueException(
                    "Could not create file directory: "
                            + file.toAbsolutePath());
        }
        batchCount.incrementAndGet();
        return file;
    }

    // use max batch size to figure out how many level of directories.
    // so we do not have more than "maxFilesPerFolder" docs in a given folder
    private String filePath(long value) {
        var nameLength = (int) ceil(log10(configuration.getMaxPerFolder()));
        var dirDepth = (int) ceil(
                log(configuration.getBatchSize())
                        / log(configuration.getMaxPerFolder()));
        var pathLength = nameLength * dirDepth;
        var path = leftPad(Long.toString(value), pathLength, '0');
        return path.replaceAll(
                "(.{" + nameLength + "})(?!$)", "$1/");
    }
}
