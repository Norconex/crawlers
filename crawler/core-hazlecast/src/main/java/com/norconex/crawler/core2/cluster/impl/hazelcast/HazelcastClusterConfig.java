/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core2.cluster.impl.hazelcast;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Hazelcast cluster configuration.
 */
@JsonAutoDetect(
    creatorVisibility = Visibility.NONE,
    fieldVisibility = Visibility.ANY)
public class HazelcastClusterConfig {

    @JsonProperty("memberAddresses")
    private List<String> memberAddresses = Collections.emptyList();

    @JsonProperty("clusterName")
    private String clusterName = "nx-crawler-cluster";

    @JsonProperty("configFile")
    @JsonSerialize(using = HazelcastConfigSerializer.class)
    @JsonDeserialize(using = HazelcastConfigDeserializer.class)
    private String configFile;

    public List<String> getMemberAddresses() {
        return memberAddresses;
    }
    public void setMemberAddresses(List<String> memberAddresses) {
        this.memberAddresses = memberAddresses;
    }

    public String getClusterName() {
        return clusterName;
    }
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getConfigFile() {
        return configFile;
    }
    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }
}