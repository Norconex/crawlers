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

import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.hdfs.HdfsFileSystemConfigBuilder;

import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractVfsFetcher;
import com.norconex.crawler.fs.fetch.impl.FileFetchUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * <p>
 * Fetcher for Apache Hadoop File System (HDFS).
 * </p>
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.crawler.fs.fetch.impl.hdfs.HdfsFetcher">
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 *   <configNames>
 *     <!-- name is repeatable -->
 *     <name>(name of configuration resource to be loaded after defaults)</name>
 *   </configNames>
 *   <configPaths>
 *     <!-- path is repeatable -->
 *     <path>(full path of configuration file to be loaded after defaults)</path>
 *   </configPaths>
 *   <configUrls>
 *     <!-- url is repeatable -->
 *     <url>(URL of configuration file to be loaded after defaults)</url>
 *   </configUrls>
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <fetcher class="HdfsFetcher"/>
 * }
 */
@SuppressWarnings("javadoc")
@ToString
@EqualsAndHashCode
public class HdfsFetcher extends AbstractVfsFetcher<HdfsFetcherConfig> {

    @Getter
    private final HdfsFetcherConfig configuration = new HdfsFetcherConfig();

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return FileFetchUtil.referenceStartsWith(fetchRequest, "hdfs://");
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        var cfg = HdfsFileSystemConfigBuilder.getInstance();
        configuration.getConfigNames().forEach(n -> cfg.setConfigName(opts, n));
        configuration.getConfigPaths().forEach(p -> cfg.setConfigPath(opts, p));
        configuration.getConfigUrls().forEach(u -> cfg.setConfigURL(opts, u));
    }
}
