/* Copyright 2023-2024 Norconex Inc.
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

import org.apache.hadoop.fs.Path;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.crawler.fs.fetch.impl.BaseAuthVfsFetcherConfig;

import lombok.Data;
import lombok.experimental.Accessors;


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
@Accessors(chain = true)
public class HdfsFetcherConfig extends BaseAuthVfsFetcherConfig {

    private final List<String> configNames = new ArrayList<>();
    @JsonIgnore
    private final List<Path> configPaths = new ArrayList<>();
    private final List<URL> configUrls = new ArrayList<>();

    public List<String> getConfigNames() {
        return Collections.unmodifiableList(configNames);
    }
    public HdfsFetcherConfig setConfigNames(List<String> configNames) {
        CollectionUtil.setAll(this.configNames, configNames);
        return this;
    }

    @JsonIgnore
    public List<Path> getConfigPaths() {
        return Collections.unmodifiableList(configPaths);
    }
    @JsonIgnore
    public HdfsFetcherConfig setConfigPaths(List<Path> configPaths) {
        CollectionUtil.setAll(this.configPaths, configPaths);
        return this;
    }

    @JsonProperty("configPaths")
    private HdfsFetcherConfig setStrConfigPaths(List<String> configPaths) {
        setConfigPaths(configPaths.stream()
                .map(Path::new)
                .toList());
        return this;
    }
    @JsonProperty("configPaths")
    private List<String> getStrConfigPaths() {
        return getConfigPaths().stream()
                .map(Path::toString)
                .toList();
    }

    public List<URL> getConfigUrls() {
        return Collections.unmodifiableList(configUrls);
    }
    public HdfsFetcherConfig setConfigUrls(List<URL> configUrls) {
        CollectionUtil.setAll(this.configUrls, configUrls);
        return this;
    }
}
