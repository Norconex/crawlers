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
package com.norconex.crawler.beam.scheduler;

import java.io.Serializable;

import lombok.Data;

/**
 * Represents the state of a host being crawled.
 * @author Norconex Inc.
 */
@Data
public class HostState implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int activeFetches = 0;
    private long lastFetchTime = 0;
    private long lastActive = System.currentTimeMillis();
    private long successfulFetches = 0;
    private long failedFetches = 0;
    private long averageResponseTimeMs = 0;
    private int consecutiveFailures = 0;
    private long robotsTxtFetchTime = 0;
    private long robotsTxtExpiry = 86400000; // 24 hours
}