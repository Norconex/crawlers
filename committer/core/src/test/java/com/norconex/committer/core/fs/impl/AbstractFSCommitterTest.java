/* Copyright 2020-2023 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThatNoException;
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

/**
 * <p>Common File Committer tests.</p>
 *
 */
public class AbstractFSCommitterTest {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest(name = "{index} {1}")
    @MethodSource(
        value = {
                "committerProvider"
        }
    )
    @interface CommitterTest {
    }

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
            AbstractFSCommitter<?, ?> c, String name
    )
            throws CommitterException {
        // write 5 upserts and 2 deletes.
        // max docs per file being 2, so should generate 4 files.
        c.getConfiguration().setDocsPerFile(2);
        setIndentIfPresent(c, 3);
        c.getConfiguration().setSplitUpsertDelete(false);

        c.init(TestUtil.committerContext(folder));
        TestUtil.commitRequests(c, TestUtil.mixedRequests(1, 0, 1, 1, 1, 0, 1));
        c.close();

        assertEquals(4, TestUtil.listFSFiles(c.getResolvedDirectory()).size());
        assertEquals(
                0, TestUtil.listFSUpsertFiles(
                        c.getResolvedDirectory()
                ).size()
        );
        assertEquals(
                0, TestUtil.listFSDeleteFiles(
                        c.getResolvedDirectory()
                ).size()
        );
    }

    @CommitterTest
    public void testSplitFileCommitter(
            AbstractFSCommitter<?, ?> c, String name
    )
            throws CommitterException {
        // write 5 upserts and 2 deletes.
        // max docs per file being 2, so should generate 3 upsert files
        // and 1 delete file
        c.getConfiguration().setDocsPerFile(2);
        setIndentIfPresent(c, 3);
        c.getConfiguration().setSplitUpsertDelete(true);

        c.init(TestUtil.committerContext(folder));
        TestUtil.commitRequests(c, TestUtil.mixedRequests(1, 0, 1, 1, 1, 0, 1));
        c.close();

        assertEquals(
                4, TestUtil.listFSFiles(
                        c.getResolvedDirectory()
                ).size()
        );
        assertEquals(
                3, TestUtil.listFSUpsertFiles(
                        c.getResolvedDirectory()
                ).size()
        );
        assertEquals(
                1, TestUtil.listFSDeleteFiles(
                        c.getResolvedDirectory()
                ).size()
        );
    }

    @CommitterTest
    public void testWriteRead(AbstractFSCommitter<?, ?> c, String name)
            throws CommitterException {

        c.getConfiguration()
                .setCompress(true)
                .setDirectory(Paths.get("c:\\temp"))
                .setDocsPerFile(5)
                .setFileNamePrefix("prefix")
                .setFileNameSuffix("suffix")
                .setSplitUpsertDelete(true);
        setIndentIfPresent(c, 3);
        assertThatNoException().isThrownBy(
                () -> TestUtil.beanMapper().assertWriteRead(c)
        );
    }

    @CommitterTest
    public void testErrors(AbstractFSCommitter<?, ?> c, String name)
            throws CommitterException, IOException {
        var fileNotFolder = folder.resolve("file");
        Files.createFile(fileNotFolder);
        c.getConfiguration().setDirectory(fileNotFolder);
        assertThatExceptionOfType(CommitterException.class).isThrownBy(() -> {
            c.init(TestUtil.committerContext(folder));
        });
    }

    private void setIndentIfPresent(AbstractFSCommitter<?, ?> c, int indent) {
        if (BeanUtil.isWritable(c, "indent")) {
            BeanUtil.setValue(c, "indent", indent);
        }
    }
}
