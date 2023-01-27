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
package com.norconex.importer.handler.tagger.impl;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.text.StringUtil;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.parser.ParseState;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Saves a copy of the document at its current processing state in
 * the specified directory. If no directory is specified, the default is
 * {@value SaveDocumentTagger#DEFAULT_SAVE_DIR_PATH}.
 * It is recommended to use this tagger as a pre-parse handler if you
 * want to save the original file.
 * </p>
 * <p>
 * Any maximum file path length value below
 * {@value StringUtil#TRUNCATE_HASH_LENGTH} is considered unlimited
 * (the default).
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.SaveDocumentTagger">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <saveDir>(target directory path where to save documents)</saveDir>
 *   <maxPathLength>
 *     (maximum file path length; defaults to -1, i.e., unlimited)
 *   </maxPathLength>
 *   <dirSplitPattern>
 *     (regular expression matching the separator(s) used to split the document
 *      reference in sub-directories)
 *   </dirSplitPattern>
 *   <escape>[false|true]</escape>
 *   <pathToField>
 *     (optional field name where to store the saved file path)
 *   </pathToField>
 *   <defaultFileName>
 *     (file name to use if a file path already exists as a directory).
 *   </defaultFileName>
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="SaveDocumentTagger">
 *   <saveDir>/save/files/here/</saveDir>
 *   <pathToField>file.path</pathToField>
 *   <defaultFileName>index.html</defaultFileName>
 * </handler>
 * }
 */
@SuppressWarnings("javadoc")
@Data
@Slf4j
@FieldNameConstants
public class SaveDocumentTagger extends AbstractDocumentTagger {

    public static final String DEFAULT_SAVE_DIR_PATH = "./savedDocuments";
    public static final String DEFAULT_SPLIT_PATTERN = "[\\:/]";
    public static final String DEFAULT_DEFAULT_FILE_NAME = "default.file";

    private Path saveDir = Path.of(DEFAULT_SAVE_DIR_PATH);
    private int maxPathLength = -1; // including saveDir, min -1 or 10
    private String dirSplitPattern = DEFAULT_SPLIT_PATTERN;
    private boolean escape;
    private String pathToField;
    private String defaultFileName = DEFAULT_DEFAULT_FILE_NAME;

    private static boolean warned = false;

    @Override
    protected void tagApplicableDocument(
            HandlerDoc doc, InputStream input, ParseState parseState)
                    throws ImporterHandlerException {

        // create relative path by splitting into directories and maybe escaping
        var rawRelativePath = StringUtils.strip(String.join("/",
                Stream.of(doc.getReference().split(dirSplitPattern))
                    .map(seg -> escape ? FileUtil.toSafeFileName(seg) : seg)
                    .toList()), "\\/");

        // define file, possibly truncated
        var file = adjustLength(saveDir.resolve(rawRelativePath));

        saveFile(doc, input, file);

        // store path to a field
        if (StringUtils.isNotBlank(pathToField)) {
            doc.getMetadata().add(pathToField,
                    file.toAbsolutePath().toString());
        }
    }

    // Done synchronously to reduce the risk of collisions when
    // dealing with default file names vs directory.  A file could be
    // renamed/written while saving occurs in another thread.
    private synchronized void saveFile(
            HandlerDoc doc, InputStream input, Path file)
            throws ImporterHandlerException {
        // if file already exists as a directory, give it a default file name,
        // within that directory
        if (Files.isDirectory(file)) {
            file = adjustLength(file.resolve(defaultFileName));
        }

        // safe file
        LOG.debug("Saving {} to {}", doc.getReference(), file);
        try {
            // rename dirs to file if need be, but we make sure not to rename
            // dirs of saveDir.
            var segFromLast = file.getNameCount() - saveDir.getNameCount();
            Path parent = null;
            for (var i = 0; i < segFromLast; i++) {
                parent = file.getParent();
                if (conflictsHandled(parent)) {
                    break;
                }
            }

            Files.createDirectories(file.getParent());
            Files.copy(input, file, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Cannot save document: " + doc.getReference(), e);
        }
    }

    private boolean conflictsHandled(Path parent) throws IOException {
        if (Files.isDirectory(parent)) {
            return true;
        }
        if (Files.isRegularFile(parent)) {
            LOG.debug("""
                Renaming file {} to {} as its original name\s\
                conflicts with the creation of a directory of\s\
                the same name.""",
                    parent, parent.resolve(defaultFileName));
            var newLocation = parent.resolve(defaultFileName);
            //TODO use importer temp dir, which should be set by caller
            // (e.g., crawler) or OS default.
            var tmpLocation = Files.createTempFile(null, null);
            Files.move(parent, tmpLocation, REPLACE_EXISTING);
            Files.createDirectories(newLocation.getParent());
            Files.move(tmpLocation, newLocation, REPLACE_EXISTING);
            return true;
        }
        return false;
    }

    private Path adjustLength(Path file) {
        if (maxPathLength < StringUtil.TRUNCATE_HASH_LENGTH) {
            return file;
        }

        var saveDirLength = saveDir.toAbsolutePath().toString().length();
        if (saveDirLength + StringUtil.TRUNCATE_HASH_LENGTH > maxPathLength) {
            if (!warned) {
                LOG.warn("The save directory path is too long to apply file "
                        + "path truncation on saved files. Save directory: {}",
                        saveDir);
            }
            return file;
        }

        var fileLength = file.toAbsolutePath().toString().length();

        if (fileLength > maxPathLength) {
            var truncatedFile = Path.of(StringUtil.truncateWithHash(
                    file.toAbsolutePath().toString(), maxPathLength));
            LOG.debug("File path '{}' was truncated to '{}'.",
                    file, truncatedFile);
            file = truncatedFile;
        }
        return file;
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setSaveDir(xml.getPath(Fields.saveDir, saveDir));
        setMaxPathLength(xml.getInteger(Fields.maxPathLength, maxPathLength));
        setDirSplitPattern(xml.getString(
                Fields.dirSplitPattern, dirSplitPattern));
        setEscape(xml.getBoolean(Fields.escape, escape));
        setPathToField(xml.getString(Fields.pathToField, pathToField));
        setDefaultFileName(xml.getString(
                Fields.defaultFileName, defaultFileName));
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.addElement(Fields.saveDir, saveDir);
        xml.addElement(Fields.maxPathLength, maxPathLength);
        xml.addElement(Fields.dirSplitPattern, dirSplitPattern);
        xml.addElement(Fields.escape, escape);
        xml.addElement(Fields.pathToField, pathToField);
        xml.addElement(Fields.defaultFileName, defaultFileName);
    }
}
