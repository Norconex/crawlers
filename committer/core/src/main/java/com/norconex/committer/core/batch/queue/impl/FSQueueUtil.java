/* Copyright 2020-2022 Norconex Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;

/**
 * File sytem queue utility methods.
 */
public final class FSQueueUtil {

    static final String EXT = ".zip";
    static final FileFilter FILTER = f -> f.getName().endsWith(EXT);

    private FSQueueUtil() {}


    /**
     * Recursively gets whether a queue directory is empty of
     * queue files.
     * @param dir directory to start looking
     * @return <code>true</code> if empty
     * @throws IOException if an I/O error occurs
     */
    public static boolean isEmpty(Path dir) throws IOException {
        return !FileUtil.dirHasFile(dir.toFile(), FILTER);
    }

    /**
     * Finds all files with the ".zip" extension from within a given
     * directory, recursively.
     * @param dir directory to start looking
     * @return a stream of Zip files.
     * @throws IOException problem occurred searching for files.
     */
    public static Stream<Path> findZipFiles(Path dir) throws IOException {
//       return Files.find(dir,  Integer.MAX_VALUE,
//                (f, a) -> FILTER.accept(f.toFile()));
        
        return Files
                .list(dir)
                .filter(s -> s.toString().endsWith(EXT))
                .sorted();
    }

    public static void toZipFile(
            CommitterRequest request, Path targetFile) throws IOException {

        try (var zipOS = new ZipOutputStream(
                IOUtils.buffer(Files.newOutputStream(targetFile)), UTF_8)) {
            // Reference
            zipOS.putNextEntry(new ZipEntry("reference"));
            IOUtils.write(request.getReference(), zipOS, UTF_8);
            zipOS.flush();
            zipOS.closeEntry();

            // Metadata
            zipOS.putNextEntry(new ZipEntry("metadata"));
            request.getMetadata().storeToProperties(zipOS);
            zipOS.flush();
            zipOS.closeEntry();

            // Content
            if (request instanceof UpsertRequest upsert) {
                zipOS.putNextEntry(new ZipEntry("content"));
                IOUtils.copy(upsert.getContent(), zipOS);
                zipOS.flush();
                zipOS.closeEntry();
            }
        }
    }

    public static CommitterRequest fromZipFile(Path sourceFile)
            throws IOException {
        return fromZipFile(sourceFile, null);
    }
    public static CommitterRequest fromZipFile(
            Path sourceFile, CachedStreamFactory streamFactory)
                    throws IOException {
        String ref = null;
        var meta = new Properties();
        CachedInputStream content = null;

        try (var zipFile = new ZipFile(sourceFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while(entries.hasMoreElements()){
                ZipEntry entry = entries.nextElement();
                var name = entry.getName();
                try (var is = zipFile.getInputStream(entry)) {
                    if ("reference".equals(name)) {
                        ref = IOUtils.toString(is, StandardCharsets.UTF_8);
                    } else if ("metadata".equals(name)) {
                        meta.loadFromProperties(is);
                    } else if ("content".equals(name)) {
                        var csf = Optional.ofNullable(
                                streamFactory).orElseGet(
                                        CachedStreamFactory::new);
                        content = csf.newInputStream(is); //NOSONAR returns it
                        content.enforceFullCaching();
                        content.rewind();
                    }
                }
            }
        }
        if (ref == null) {
            throw new IOException("Committer queue zip contains no "
                    + "\"reference\" file: " + sourceFile);
        }

        if (content == null) {
            return new DeleteRequest(ref, meta);
        }
        return new UpsertRequest(ref, meta, content);
    }
}
