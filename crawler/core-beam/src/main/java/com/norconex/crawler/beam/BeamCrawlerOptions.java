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
package com.norconex.crawler.beam;

import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;

/**
 * Command-line options for the Beam Crawler.
 * @author Norconex Inc.
 */
public interface BeamCrawlerOptions extends PipelineOptions {
    
    @Description("Path to the crawler configuration file")
    @Default.String("config.yaml")
    String getConfigFile();
    void setConfigFile(String value);
    
    @Description("Whether to run as a continuous crawler that never terminates")
    @Default.Boolean(false)
    boolean isContinuousCrawl();
    void setContinuousCrawl(boolean value);
    
    @Description("Tenant ID for multi-tenant support")
    @Default.String("default")
    String getTenantId();
    void setTenantId(String value);
    
    @Description("Maximum number of concurrent connections per host")
    @Default.Integer(1)
    int getMaxConnectionsPerHost();
    void setMaxConnectionsPerHost(int value);
    
    @Description("Minimum delay between requests to the same host (milliseconds)")
    @Default.Integer(1000)
    int getMinDelayBetweenRequests();
    void setMinDelayBetweenRequests(int value);
    
    @Description("Maximum number of retries for failed requests")
    @Default.Integer(3)
    int getMaxRetries();
    void setMaxRetries(int value);
    
    @Description("Whether to detect and process only incremental changes")
    @Default.Boolean(true)
    boolean isIncrementalCrawling();
    void setIncrementalCrawling(boolean value);
    
    @Description("Checkpoint interval in minutes (for resuming after failures)")
    @Default.Integer(15)
    int getCheckpointIntervalMinutes();
    void setCheckpointIntervalMinutes(int value);
}