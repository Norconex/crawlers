/* Copyright 2014-2022 Norconex Inc.
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
package com.norconex.crawler.core.pipeline.importer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.pipeline.IPipelineStage;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.crawler.CrawlerException;

/**
 * Common pipeline stage for saving documents.
 */
public class SaveDocumentStage
        implements IPipelineStage<ImporterPipelineContext> {

    private static final Logger LOG =
            LoggerFactory.getLogger(SaveDocumentStage.class);

    private static final int MAX_SEGMENT_LENGTH = 25;

    @Override
    public boolean execute(ImporterPipelineContext ctx) {
        //TODO have an interface for how to store downloaded files
        //(i.e., location, directory structure, file naming)
        var workdir = ctx.getCrawler().getWorkDir();
        var downloadDir = workdir.resolve("downloads");
        if (!downloadDir.toFile().exists()) {
            try {
                Files.createDirectories(downloadDir);
            } catch (IOException e) {
                throw new CrawlerException(
                        "Cannot create download directory: " + downloadDir, e);
            }
        }
        var path = urlToPath(ctx.getDocRecord().getReference());

        var downloadFile = downloadDir.resolve(path);

        LOG.debug("Saved file: {}", downloadFile);
        try (OutputStream out =
                FileUtils.openOutputStream(downloadFile.toFile())) {
            IOUtils.copy(ctx.getDocument().getInputStream(), out);
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.DOCUMENT_SAVED)
                    .source(ctx.getCrawler())
                    .crawlDocRecord(ctx.getDocRecord())
                    .subject(downloadFile)
                    .build());
        } catch (IOException e) {
            throw new CrawlerException("Cannot save document: "
                            + ctx.getDocRecord().getReference(), e);
        }
        return true;
    }

    public static String urlToPath(final String url) {
        if (url == null) {
            return null;
        }

        var domain = url.replaceFirst("(.*?)(://)(.*?)(/)(.*)", "$1_$3");
        domain = domain.replaceAll("[\\W]+", "_");
        var path = url.replaceFirst("(.*?)(://)(.*?)(/)(.*)", "$5");

        var segments = path.split("[\\/]");
        var b = new StringBuilder();
        for (var i = 0; i < segments.length; i++) {
            var segment = segments[i];
            if (StringUtils.isNotBlank(segment)) {
                var lastSegment = (i + 1) == segments.length;
                var segParts = splitLargeSegment(segment);
                for (var j = 0; j < segParts.length; j++) {
                    var segPart = segParts[j];
                    if (b.length() > 0) {
                        b.append(File.separatorChar);
                    }
                    // Prefixes directories or files with different letter
                    // to ensure directory and files can't have the same
                    // names (github #44).
                    if (lastSegment && (j + 1) == segParts.length) {
                        b.append("f.");
                    } else {
                        b.append("d.");
                    }
                    b.append(FileUtil.toSafeFileName(segPart));
                }
            }
        }
        if (b.length() > 0) {
            return "d." + domain + File.separatorChar + b.toString();
        }
        return "f." + domain;
    }

    private static String[] splitLargeSegment(String segment) {
        if (segment.length() <= MAX_SEGMENT_LENGTH) {
            return new String[] { segment };
        }

        List<String> segments = new ArrayList<>();
        var b = new StringBuilder(segment);
        while (b.length() > MAX_SEGMENT_LENGTH) {
            segments.add(b.substring(0, MAX_SEGMENT_LENGTH));
            b.delete(0, MAX_SEGMENT_LENGTH);
        }
        segments.add(b.substring(0));
        return segments.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }
}