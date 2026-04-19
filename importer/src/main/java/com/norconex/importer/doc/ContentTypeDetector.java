/* Copyright 2014-2026 Norconex Inc.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypesFactory;

import com.norconex.commons.lang.file.ContentType;

import lombok.extern.slf4j.Slf4j;

/**
 * Main class to detect all content types.  This class is thread-safe.
 */
@Slf4j
public final class ContentTypeDetector {

    private static final Pattern EXTENSION_PATTERN =
            Pattern.compile("^.*(\\.[A-z0-9]+).*");
    private static final MimeTypes CUSTOM_MIME_TYPES;
    private static final Detector DETECTOR;
    static {
        configureCustomMimeTypes();
        CUSTOM_MIME_TYPES = createCustomMimeTypes();
        DETECTOR = new DefaultDetector(CUSTOM_MIME_TYPES);
    }

    private static void configureCustomMimeTypes() {
        if (System
                .getProperty(MimeTypesFactory.CUSTOM_MIMES_SYS_PROP) != null) {
            return;
        }
        var resource = ContentTypeDetector.class.getClassLoader().getResource(
                "org/apache/tika/mime/custom-mimetypes.xml");
        if (resource == null) {
            return;
        }
        try {
            if ("file".equalsIgnoreCase(resource.getProtocol())) {
                var path = Path.of(resource.toURI());
                System.setProperty(
                        MimeTypesFactory.CUSTOM_MIMES_SYS_PROP,
                        path.toString());
                return;
            }
            try (var in = resource.openStream()) {
                var temp =
                        Files.createTempFile("tika-custom-mimetypes-", ".xml");
                Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
                temp.toFile().deleteOnExit();
                System.setProperty(
                        MimeTypesFactory.CUSTOM_MIMES_SYS_PROP,
                        temp.toString());
            }
        } catch (Exception e) {
            LOG.warn("Could not register custom mime types resource.", e);
        }
    }

    private static MimeTypes createCustomMimeTypes() {
        try {
            return MimeTypesFactory.create(
                    "tika-mimetypes.xml",
                    "custom-mimetypes.xml",
                    ContentTypeDetector.class.getClassLoader());
        } catch (IOException | MimeTypeException e) {
            LOG.warn(
                    "Could not load custom mime types; falling back to defaults.",
                    e);
            return MimeTypes.getDefaultMimeTypes();
        }
    }

    /**
     * Returns the custom MIME type detector used for content type detection.
     * This detector is pre-configured with Norconex custom MIME types
     * (e.g., application/vnd.xfdl) and can be used to configure
     * Tika parsers to recognise the same types.
     * @return the custom Tika detector
     */
    public static Detector getDetector() {
        return DETECTOR;
    }

    private ContentTypeDetector() {
    }

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
        return doDetect(content, null);
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

    //NOTE: important not to close the stream here. Will be handled by caller
    // if need be.
    private static ContentType doDetect(
            InputStream is, String fileName) throws IOException {
        var tikaStream = TikaInputStream.get(is);
        var meta = new Metadata();
        if (StringUtils.isNotBlank(fileName)) {
            var extension =
                    EXTENSION_PATTERN.matcher(fileName).replaceFirst("$1");
            meta.set(
                    TikaCoreProperties.RESOURCE_NAME_KEY,
                    "file:///detect" + extension);
        }
        var media = DETECTOR.detect(tikaStream, meta);

        LOG.debug("Detected \"{}\" content-type for file: {}", media, fileName);
        return ContentType.valueOf(media.toString());
    }
}
