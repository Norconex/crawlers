/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.importer.doc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import com.norconex.commons.lang.file.ContentType;

import lombok.extern.slf4j.Slf4j;

/**
 * Main class to detect all content types.  This class is thread-safe.
 */
@Slf4j
public final class ContentTypeDetector {

    private static final Pattern EXTENSION_PATTERN =
            Pattern.compile("^.*(\\.[A-z0-9]+).*");
    private static final Tika TIKA = new Tika();

    private ContentTypeDetector() {}

    /**
     * Detects the content type of the given file.
     * @param file file on which to detect content type
     * @return the detected content type
     * @throws IOException problem detecting content type
     */
    public static ContentType detect(File file) throws IOException {
        return detect(file, file.getName());
    }
    /**
     * Detects the content type of the given file.
     * @param file file on which to detect content type
     * @param fileName a file name which can help influence detection
     * @return the detected content type
     * @throws IOException problem detecting content type
     */
    public static ContentType detect(
            File file, String fileName) throws IOException {
        var safeFileName = fileName;
        if (StringUtils.isBlank(safeFileName)) {
            safeFileName = file.getName();
        }
        return doDetect(TikaInputStream.get(file.toPath()), safeFileName);
    }
    /**
     * Detects the content type from the given input stream.
     * @param content the content on which to detect content type
     * @return the detected content type
     * @throws IOException problem detecting content type
     */
    public static ContentType detect(InputStream content) throws IOException {
        var type = TIKA.detect(content);
        LOG.debug("Detected \"{}\" content-type for input stream.", type);
        return ContentType.valueOf(type);
    }
    /**
     * Detects the content type from the given input stream.
     * @param content the content on which to detect content type
     * @param fileName a file name which can help influence detection
     * @return the detected content type
     * @throws IOException problem detecting content type
     */
    public static ContentType detect(InputStream content, String fileName)
            throws IOException {
        return doDetect(content, fileName);
    }

    private static ContentType doDetect(
            InputStream is, String fileName) throws IOException {
        try (var tikaStream = TikaInputStream.get(is)) {
            var meta = new Metadata();
            var extension = EXTENSION_PATTERN.matcher(
                    fileName).replaceFirst("$1");
            meta.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                    "file:///detect" + extension);
            var media = TIKA.getDetector().detect(tikaStream, meta);

            LOG.debug("Detected \"{}\" content-type for: {}", media, fileName);
            return ContentType.valueOf(media.toString());
        }
    }
}
