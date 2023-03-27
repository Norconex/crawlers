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
package com.norconex.crawler.fs.fetch.impl;

import java.io.File;
import java.io.IOException;

import org.apache.ftpserver.ftplet.FtpException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

class FtpsFetcherTest extends AbstractFileFetcherTest {

    private static MockFtpServer server;

    @BeforeAll
    static void beforeAll(@TempDir File tempDir)
            throws FtpException, IOException {
        server = new MockFtpServer(tempDir, true);
        server.start();
    }
    @AfterAll
    static void afterAll() throws FtpException {
        MockFtpServer.stop(server);
    }

    public FtpsFetcherTest() {
        super(MockFtpServer.fetcherClient());
    }

    @Override
    String getStartPath() {
        return server.getStartPath();
    }
}
