/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.fs.fetch.impl.cmis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.fs.FsTestUtil;

class CmisAtomFileSystemTest {

    @TempDir
    private Path tempDir;

    private static CmisTestServer cmisServer;

    @BeforeAll
    static void beforeClass() throws Exception {
        cmisServer = new CmisTestServer();
        cmisServer.start();
    }
    @AfterAll
    static void afterClass() throws Exception {
        if (cmisServer != null) {
            cmisServer.stop();
            cmisServer = null;
        }
    }

    @Test
    void testAtom_1_0() {
        testCmisFileSystem(CmisTestServer.ATOM_1_0, 21);
    }
    @Test
    void testAtom_1_1() {
        testCmisFileSystem(CmisTestServer.ATOM_1_1, 21);
    }


    void testCmisFileSystem(String path, int expectedQty) {

        var ref = cmisEndpointUrl(path);
        var mem = FsTestUtil.runWithConfig(
                tempDir,
                cfg -> cfg
                .setStartReferences(List.of(ref))
                .setFetchers(List.of(new CmisFetcher())));
        assertThat(mem.getUpsertCount()).isEqualTo(expectedQty);
    }

    private String cmisEndpointUrl(String path) {
        return "cmis:http://localhost:" + cmisServer.getLocalPort() + path;
    }
}
