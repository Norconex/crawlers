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
 * Configuration for the scheduler component.
 * @author Norconex Inc.
 */
@Data
public class SchedulerConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int maxConcurrentRequests = 100;
    private int minDelayBetweenRequestsMs = 1000;
    private int maxRequestsPerHost = 1;
    private int maxRetries = 3;
    private int retryDelayMs = 5000;
    private boolean respectRobotsTxt = true;
    private int politenessDelayMs = 500;
    private boolean enableAdaptivePoliteness = true;
    private int schedulerThreads = 10;
    private long idleTimeoutMs = 60000;
}