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
package com.norconex.committer.core.fs.impl;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.TestUtil;
import com.norconex.committer.core.fs.AbstractFSCommitter;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>Common File Committer tests.</p>
 *
 */
public class AbstractFSCommitterTest  {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest(name = "{index} {1}")
    @MethodSource(value= {
            "committerProvider"
    })
    @interface CommitterTest {}

    static Stream<Arguments> committerProvider() {
        return Stream.of(
            TestUtil.args(new XMLFileCommitter()),
            TestUtil.args(new JSONFileCommitter()),
            TestUtil.args(new CSVFileCommitter())
        );
    }

    @TempDir
    public Path folder;

    @CommitterTest
    public void testMergedFileCommitter(
            AbstractFSCommitter<?> c, String name) throws CommitterException {
        // write 5 upserts and 2 deletes.
        // max docs per file being 2, so should generate 4 files.
        c.setDocsPerFile(2);
        setIndentIfPresent(c, 3);
        c.setSplitUpsertDelete(false);


        c.init(TestUtil.committerContext(folder));
        TestUtil.commitRequests(c, TestUtil.mixedRequests(1, 0, 1, 1, 1, 0, 1));
        c.close();

        assertEquals(4,  TestUtil.listFSFiles(c.getDirectory()).size());
        assertEquals(0,  TestUtil.listFSUpsertFiles(c.getDirectory()).size());
        assertEquals(0,  TestUtil.listFSDeleteFiles(c.getDirectory()).size());
    }

    @CommitterTest
    public void testSplitFileCommitter(
            AbstractFSCommitter<?> c, String name) throws CommitterException {
        // write 5 upserts and 2 deletes.
        // max docs per file being 2, so should generate 3 upsert files
        // and 1 delete file
        c.setDocsPerFile(2);
        setIndentIfPresent(c, 3);
        c.setSplitUpsertDelete(true);

        c.init(TestUtil.committerContext(folder));
        TestUtil.commitRequests(c, TestUtil.mixedRequests(1, 0, 1, 1, 1, 0, 1));
        c.close();

        assertEquals(4,  TestUtil.listFSFiles(c.getDirectory()).size());
        assertEquals(3,  TestUtil.listFSUpsertFiles(c.getDirectory()).size());
        assertEquals(1,  TestUtil.listFSDeleteFiles(c.getDirectory()).size());
    }

    @CommitterTest
    public void testWriteRead(AbstractFSCommitter<?> c, String name)
            throws CommitterException {

        c.setCompress(true);
        c.setDirectory(Paths.get("c:\\temp"));
        c.setDocsPerFile(5);
        c.setFileNamePrefix("prefix");
        c.setFileNamePrefix("suffix");
        setIndentIfPresent(c, 3);
        c.setSplitUpsertDelete(true);
        XML.assertWriteRead(c, "committer");
    }

    @CommitterTest
    public void testErrors(AbstractFSCommitter<?> c, String name)
            throws CommitterException, IOException {
        var fileNotFolder = folder.resolve("file");
        Files.createFile(fileNotFolder);
        c.setDirectory(fileNotFolder);
        assertThatExceptionOfType(CommitterException.class).isThrownBy(() -> {
            c.init(TestUtil.committerContext(folder));
        });
    }

    private void setIndentIfPresent(AbstractFSCommitter<?> c, int indent) {
        if (BeanUtil.isWritable(c, "indent")) {
            BeanUtil.setValue(c, "indent", indent);
        }
    }
}