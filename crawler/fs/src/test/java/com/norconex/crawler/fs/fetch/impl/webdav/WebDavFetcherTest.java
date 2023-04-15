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
package com.norconex.crawler.fs.fetch.impl.webdav;

import java.io.File;

import org.apache.commons.vfs2.FileSystemException;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.norconex.crawler.fs.FsStubber;
import com.norconex.crawler.fs.fetch.FileFetcher;
import com.norconex.crawler.fs.fetch.impl.AbstractFileFetcherTest;

@Testcontainers(disabledWithoutDocker = true)
class WebDavFetcherTest extends AbstractFileFetcherTest {

    @Container
    public GenericContainer<?> webdavContainer =
        new GenericContainer<>(DockerImageName.parse(
                "mwader/webdav:update-to-go-1.12"))
            .withExposedPorts(8080)
            .withFileSystemBind(
                    new File(FsStubber.MOCK_FS_PATH).getAbsolutePath(),
                    "/webdav", BindMode.READ_ONLY)
            ;

    private String webdavUrl;

    @Override
    protected FileFetcher fetcher() {
        return fetcherClient();
    }

    public static WebDavFetcher fetcherClient() {
        return new WebDavFetcher();
    }

    @Override
    protected String getStartPath() {
        return webdavUrl;
    }

    @BeforeEach
    protected void setUp() throws FileSystemException {
        var host = webdavContainer.getHost();
        var port = webdavContainer.getFirstMappedPort();
        webdavUrl = "webdav://%s:%s".formatted(host, port);
    }
}
