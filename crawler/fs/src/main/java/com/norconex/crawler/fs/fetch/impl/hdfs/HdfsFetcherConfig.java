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
 * Configuration for {@link HdfsFetcher}.
 * </p>
 */
@Data
@Accessors(chain = true)
public class HdfsFetcherConfig extends BaseAuthVfsFetcherConfig {

    private final List<String> configNames = new ArrayList<>();
    @JsonIgnore
    private final List<Path> configPaths = new ArrayList<>();
    private final List<URL> configUrls = new ArrayList<>();

    /**
     * Gets the names of configuration resources to be loaded after defaults.
     * @return list of names
     */
    public List<String> getConfigNames() {
        return Collections.unmodifiableList(configNames);
    }

    /**
     * Sets the names of configuration resources to be loaded after defaults.
     * @param configNames list of names
     * @return this
     */
    public HdfsFetcherConfig setConfigNames(List<String> configNames) {
        CollectionUtil.setAll(this.configNames, configNames);
        return this;
    }

    /**
     * Gets the full paths of configuration files to be loaded after defaults.
     * @return list of paths
     */
    @JsonIgnore
    public List<Path> getConfigPaths() {
        return Collections.unmodifiableList(configPaths);
    }

    /**
     * Sets the full paths of configuration files to be loaded after defaults.
     * @param configPaths list of paths
     * @return this
     */
    @JsonIgnore
    public HdfsFetcherConfig setConfigPaths(List<Path> configPaths) {
        CollectionUtil.setAll(this.configPaths, configPaths);
        return this;
    }

    @JsonProperty("configPaths")
    private HdfsFetcherConfig setStrConfigPaths(List<String> configPaths) {
        setConfigPaths(
                configPaths.stream()
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

    /**
     * Gets the URLs of configuration files to be loaded after defaults.
     * @return list of URLs
     */
    public List<URL> getConfigUrls() {
        return Collections.unmodifiableList(configUrls);
    }

    /**
     * Sets the URLs of configuration files to be loaded after defaults.
     * @param configUrls list of URLs
     * @return this
     */
    public HdfsFetcherConfig setConfigUrls(List<URL> configUrls) {
        CollectionUtil.setAll(this.configUrls, configUrls);
        return this;
    }
}
