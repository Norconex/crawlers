/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.fs.pipeline.queue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;

import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.core.crawler.CrawlerImpl.QueueInitContext;
import com.norconex.crawler.fs.crawler.StartPathsProvider;
import com.norconex.crawler.fs.doc.FsDocRecord;
import com.norconex.crawler.fs.util.Fs;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class FsQueueInitializer
        implements Function<QueueInitContext, MutableBoolean> {

    @Override
    public MutableBoolean apply(QueueInitContext ctx) {
        //TODO does it make sense to have an "async" launch option like Web?
        LOG.info("Queuing start paths synchronously.");
        queueStartPaths(ctx);
        return new MutableBoolean(true);
    }

    private void queueStartPaths(QueueInitContext ctx) {
        var urlCount = 0;
        urlCount += queueStartPathsRegular(ctx);
        urlCount += queueStartPathsSeedFiles(ctx);
        urlCount += queueStartPathsProviders(ctx);
        LOG.info(NumberFormat.getNumberInstance().format(urlCount)
                + " start paths identified.");
    }

    //TODO these methods are nearly identical to Web equivalents. Move to Core?

    private int queueStartPathsRegular(final QueueInitContext ctx) {
        // Queue regular start urls
        var startPaths = Fs.config(ctx.getCrawler()).getStartPaths();
        for (String startPath : startPaths) {
            // No protocol specified: we assume local file, and we get
            // the absolute version.
            if (!startPath.contains("://")) {
                startPath = new File(startPath).getAbsolutePath();
            }
            ctx.queue(new FsDocRecord(startPath, 0));
        }
        if (!startPaths.isEmpty()) {
            LOG.info("Queued {} regular start paths.", startPaths.size());
        }
        return startPaths.size();
    }

    private int queueStartPathsSeedFiles(final QueueInitContext ctx) {
        var pathsFiles = Fs.config(ctx.getCrawler()).getPathsFiles();
        var pathCount = 0;
        for (Path pathsFile : pathsFiles) {
            try (var it = IOUtils.lineIterator(
                    Files.newInputStream(pathsFile), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var startPath = StringUtils.trimToNull(it.nextLine());
                    if (startPath != null && !startPath.startsWith("#")) {
                        ctx.queue(new FsDocRecord(startPath, 0));
                        pathCount++;
                    }
                }
            } catch (IOException e) {
                throw new CrawlerException(
                        "Could not process paths file: " + pathsFile, e);
            }
        }
        if (pathCount > 0) {
            LOG.info("Queued {} start paths from {} seed files.",
                    pathCount, pathsFiles.size());
        }
        return pathCount;
    }

    private int queueStartPathsProviders(final QueueInitContext ctx) {
        var providers =  Fs.config(ctx.getCrawler()).getStartPathsProviders();
        var count = 0;
        for (StartPathsProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            var it = provider.provideStartPaths();
            while (it.hasNext()) {
                ctx.queue(new FsDocRecord(it.next(), 0));
                count++;
            }
        }
        if (count > 0) {
            LOG.info("Queued {} paths from {} path providers.",
                    count, providers.size());
        }
        return count;
    }
}
