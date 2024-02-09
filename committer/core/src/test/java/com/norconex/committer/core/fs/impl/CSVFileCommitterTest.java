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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.TestUtil;

/**
 * <p>CSV File Committer tests.</p>
 *
 */
class CSVFileCommitterTest  {

    @TempDir
    public Path folder;

    @Test
    void testMergedCSVFileCommitter()
            throws CommitterException, IOException {
        var expected = """
            type,URL,title,content
            upsert,http://example.com/1,title 1,content 1
            delete,http://example.com/2,title 2,
            upsert,http://example.com/3,title 3,content 3
            """;

        var c = commitSampleData(false);

        var files = TestUtil.listFSFiles(c.getResolvedDirectory());
        Assertions.assertEquals(1,  files.size());

        var actual = FileUtils.readFileToString(
                files.iterator().next(), StandardCharsets.UTF_8);

        Assertions.assertEquals(expected, actual);
    }

    @Test
    void testSplitCSVFileCommitter()
            throws CommitterException, IOException {

        var expectedUpsert = """
            URL,title,content
            http://example.com/1,title 1,content 1
            http://example.com/3,title 3,content 3
            """;

        var expectedDelete =
                "URL,title,content\n"
              + "http://example.com/2,title 2,\n";

        var c = commitSampleData(true);

        var files = TestUtil.listFSFiles(c.getResolvedDirectory());
        Assertions.assertEquals(2,  files.size());

        var actualUpsert = FileUtils.readFileToString(
                TestUtil.listFSUpsertFiles(
                        c.getResolvedDirectory()).iterator().next(), UTF_8);
        var actualDelete = FileUtils.readFileToString(
                TestUtil.listFSDeleteFiles(
                        c.getResolvedDirectory()).iterator().next(), UTF_8);

        Assertions.assertEquals(expectedUpsert, actualUpsert);
        Assertions.assertEquals(expectedDelete, actualDelete);
    }

    private CSVFileCommitter commitSampleData(
            boolean splitUpsertDelete) throws CommitterException {
        List<CommitterRequest> reqs = new ArrayList<>();
        reqs.add(TestUtil.upsertRequest("http://example.com/1", "content 1",
                "type", "upsert",
                "document.reference", "http://example.com/1",
                "title", "title 1",
                "noise", "blah1"));
        reqs.add(TestUtil.deleteRequest("http://example.com/2",
                "type", "delete",
                "document.reference", "http://example.com/2",
                "title", "title 2",
                "noise", "blah2"));
        reqs.add(TestUtil.upsertRequest("http://example.com/3", "content 3",
                "type", "upsert",
                "document.reference", "http://example.com/3",
                "title", "title 3",
                "noise", "blah3"));

        var c = new CSVFileCommitter();
        // write 5 upserts and 2 deletes.
        // max docs per file being 2, so should generate 4 files.
        c.getConfiguration()
            .setColumns(List.of(
                    new CSVColumn()
                        .setField("document.reference")
                        .setHeader("URL"),
                    new CSVColumn()
                        .setField("title")
                        .setHeader("title"),
                    new CSVColumn()
            ))
            .setShowHeaders(true)
            .setDocsPerFile(20)
            .setSplitUpsertDelete(splitUpsertDelete);

        c.init(TestUtil.committerContext(folder));
        TestUtil.commitRequests(c, reqs);
        c.close();
        return c;
    }

    @Test
    void testWriteRead() throws CommitterException {
        var c = new CSVFileCommitter();
        c.getConfiguration()
            .setColumns(List.of(
                    new CSVColumn()
                        .setField("document.reference")
                        .setHeader("URL")
                        .setTruncateAt(2000),
                    new CSVColumn()
                        .setField("title")
                        .setHeader("My Title")
                        .setTruncateAt(100),
                    new CSVColumn()
                        .setHeader("My content")
                        .setTruncateAt(200)
            ))
            .setFormat(CSVFileCommitterConfig.Format.EXCEL)
            .setDelimiter('|')
            .setQuote('!')
            .setShowHeaders(true)
            .setEscape('%')
            .setTruncateAt(10)
            .setTypeHeader("Request Type")
            .setMultiValueJoinDelimiter("^^^")
            .setDirectory(Paths.get("c:\\temp"))
            .setDocsPerFile(5)
            .setFileNamePrefix("prefix")
            .setFileNamePrefix("suffix")
            .setSplitUpsertDelete(true)
            .setCompress(true);

        assertThatNoException().isThrownBy(
                () -> TestUtil.beanMapper().assertWriteRead(c));
    }
}
