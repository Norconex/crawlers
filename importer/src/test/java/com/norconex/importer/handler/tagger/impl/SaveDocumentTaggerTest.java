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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class SaveDocumentTaggerTest {

    private static final String FLD_PATH = "file.path";

    @TempDir
    private Path tempDir;

    @Test
    void testSaveDocumentTagger() throws ImporterHandlerException, IOException {
        var t = new SaveDocumentTagger();
        t.setEscape(true);
        t.setPathToField(FLD_PATH);
        t.setSaveDir(tempDir.resolve("basic"));

        var doc = tag(t, "http://example.com/my$file.html");
        assertThat(savedFile(doc)).isRegularFile();
        tag(t, "/with/stripables/");
        assertThat(listFilePaths(t.getSaveDir())).containsExactly(
                "http/example.com/my_36_file.html",
                "with/stripables");
    }
    @Test
    void testParentIsFileConflict()
            throws ImporterHandlerException, IOException {
        var t = new SaveDocumentTagger();
        t.setDefaultFileName("index.html");
        t.setPathToField(FLD_PATH);
        t.setSaveDir(tempDir.resolve("parentIsFile"));

        var doc1 = tag(t, "some/test");
        assertThat(savedFile(doc1)).isRegularFile();
        var doc2 = tag(t, "some/test/surprise");
        assertThat(savedFile(doc1)).isDirectory();
        assertThat(savedFile(doc1).resolve("index.html")).isRegularFile();
        assertThat(savedFile(doc2)).isRegularFile();

        assertThat(listFilePaths(t.getSaveDir())).containsExactly(
                "some/test/index.html",
                "some/test/surprise");
    }

    @Test
    void testTargetIsDirConflict()
            throws ImporterHandlerException, IOException {
        var t = new SaveDocumentTagger();
        t.setDefaultFileName("index.html");
        t.setPathToField(FLD_PATH);
        t.setSaveDir(tempDir.resolve("targetIsDir"));

        var doc1 = tag(t, "some/test/file.txt");
        assertThat(savedFile(doc1)).isRegularFile();
        var doc2 = tag(t, "some/test");
        assertThat(savedFile(doc1)).isRegularFile();
        assertThat(savedFile(doc2).resolve("index.html")).isRegularFile();
        assertThat(savedFile(doc2)).isDirectory();

        assertThat(listFilePaths(t.getSaveDir())).containsExactly(
                "some/test/file.txt",
                "some/test/index.html");
    }

    @Test
    void testMaxPathLength()
            throws ImporterHandlerException, IOException {
        var t = new SaveDocumentTagger();
        t.setDefaultFileName("index.html");
        t.setPathToField(FLD_PATH);
        t.setSaveDir(tempDir.resolve("maxLengthDir"));

        var saveDirLength = t.getSaveDir().toAbsolutePath().toString().length();

        // Max length is too short
        t.setMaxPathLength(4);
        tag(t, "some/long/path/for/file1.txt");

        // Save dir path is too long to apply truncation
        t.setMaxPathLength(saveDirLength + 4);
        tag(t, "some/long/path/for/file2.txt");

        assertThat(listFilePaths(t.getSaveDir())).containsExactly(
                "some/long/path/for/file1.txt",
                "some/long/path/for/file2.txt");

        // Apply truncation
        t.setMaxPathLength(saveDirLength + 18);
        var doc = tag(t, "some/long/path/for/file3.txt");
        assertThat(savedFile(doc))
            .isRegularFile()
            .matches(p -> p.getFileName().toString().matches("^lo\\d+$"));
    }

    // relative to dir, forward slashed
    private List<String> listFilePaths(Path dir) throws IOException {
        return Files.find(tempDir, 10, (p, a) -> Files.isRegularFile(p))
                .map(p -> dir.relativize(p))
                .map(Path::toString)
                .map(n -> n.replace('\\', '/'))
                .toList();
    }
    private Path savedFile(HandlerDoc doc) {
        return Path.of(doc.getMetadata().getString(FLD_PATH));
    }
    private HandlerDoc tag(SaveDocumentTagger t, String ref)
            throws ImporterHandlerException {
        var is = TestUtil.toInputStream("blah");
        var doc = TestUtil.newHandlerDoc(ref, is);
        t.tagDocument(doc, is, ParseState.PRE);
        return doc;
    }

    @Test
    void testWriteRead() {
        var t = new SaveDocumentTagger();
        t.addRestriction(new PropertyMatcher(
                TextMatcher.basic("field"), TextMatcher.basic("value")));
        t.setDefaultFileName("index.html");
        t.setDirSplitPattern("__");
        t.setEscape(true);
        t.setMaxPathLength(255);
        t.setPathToField("myfield");
        t.setSaveDir(Path.of("/somewhere/"));
        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(t, "handler"));
    }
}
