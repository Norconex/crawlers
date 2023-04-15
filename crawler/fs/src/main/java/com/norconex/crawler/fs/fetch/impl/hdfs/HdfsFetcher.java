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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.hdfs.HdfsFileSystemConfigBuilder;
import org.apache.hadoop.fs.Path;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractVfsFetcher;
import com.norconex.crawler.fs.fetch.impl.FileFetchUtil;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;


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
@Data
@FieldNameConstants
public class HdfsFetcher extends AbstractVfsFetcher {

    private final List<String> configNames = new ArrayList<>();
    private final List<Path> configPaths = new ArrayList<>();
    private final List<URL> configUrls = new ArrayList<>();

    public List<String> getConfigNames() {
        return Collections.unmodifiableList(configNames);
    }
    public void setConfigNames(List<String> configNames) {
        CollectionUtil.setAll(configNames, configNames);
    }

    public List<Path> getConfigPaths() {
        return Collections.unmodifiableList(configPaths);
    }
    public void setConfigPaths(List<Path> configPaths) {
        CollectionUtil.setAll(this.configPaths, configPaths);
    }

    public List<URL> getConfigUrls() {
        return Collections.unmodifiableList(configUrls);
    }
    public void setConfigUrls(List<URL> configUrls) {
        CollectionUtil.setAll(configUrls, configUrls);
    }

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return FileFetchUtil.referenceStartsWith(fetchRequest, "hdfs://");
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        var cfg = HdfsFileSystemConfigBuilder.getInstance();
        configNames.forEach(n -> cfg.setConfigName(opts, n));
        configPaths.forEach(p -> cfg.setConfigPath(opts, p));
        configUrls.forEach(u -> cfg.setConfigURL(opts, u));
    }

    @Override
    protected void loadFetcherFromXML(XML xml) {
        setConfigNames(xml.getStringList(
                Fields.configNames + "/name", configNames));
        if (xml.isElementPresent(Fields.configPaths)) {
            setConfigPaths(xml.getStringList(Fields.configPaths + "/path")
                    .stream()
                    .map(Path::new)
                    .toList());
        }
        setConfigUrls(xml.getURLList(Fields.configUrls + "/url", configUrls));
    }
    @Override
    protected void saveFetcherToXML(XML xml) {
        xml.addElementList(Fields.configNames, "name", configNames);
        xml.addElementList(Fields.configPaths, "path", configPaths);
        xml.addElementList(Fields.configUrls, "url", configUrls);
    }
}
