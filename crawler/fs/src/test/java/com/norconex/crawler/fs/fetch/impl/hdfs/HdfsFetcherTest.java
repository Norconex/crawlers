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
package com.norconex.crawler.fs.fetch.impl.hdfs;

import static com.norconex.crawler.core.fetch.FetchDirective.DOCUMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.hdfs.HdfsFileSystemConfigBuilder;
import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.importer.doc.DocRecord;

class HdfsFetcherTest {

    //TODO find a way to unit test HDFS.

    @Test
    void testHdfsFetcher() throws MalformedURLException {
        List<String> names = List.of("name1", "name2");
        List<Path> paths = List.of(new Path("/path1"), new Path("/path2"));
        List<URL> urls = List.of(
                new URL("http://url1.com"),
                new URL("http://url2.com"));


        var f = new HdfsFetcher();
        assertThatNoException().isThrownBy(() -> {
            f.getConfiguration()
                .setConfigNames(names)
                .setConfigPaths(paths)
                .setConfigUrls(urls);
            assertThatNoException().isThrownBy(() ->
                    BeanMapper.DEFAULT.assertWriteRead(f));
        });

        assertThat(f.getConfiguration().getConfigNames())
            .containsExactlyElementsOf(names);
        assertThat(f.getConfiguration().getConfigPaths())
            .containsExactlyElementsOf(paths);
        assertThat(f.getConfiguration().getConfigUrls())
            .containsExactlyElementsOf(urls);

        assertThat(f.acceptRequest(new FileFetchRequest(new CrawlDoc(
                new DocRecord("hdfs://blah")), DOCUMENT))).isTrue();
        assertThat(f.acceptRequest(new FileFetchRequest(new CrawlDoc(
                new DocRecord("http://blah")), DOCUMENT))).isFalse();

        var opts = new FileSystemOptions();
        f.applyFileSystemOptions(opts);
        var cfg = HdfsFileSystemConfigBuilder.getInstance();
        assertThat(cfg.getConfigNames(opts)).containsExactlyElementsOf(names);
        assertThat(cfg.getConfigPaths(opts)).containsExactlyElementsOf(paths);
        assertThat(cfg.getConfigURLs(opts)).containsExactlyElementsOf(urls);

    }
}
