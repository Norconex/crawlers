/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.grid;

import java.util.concurrent.Future;

public interface GridServices {

    /**
     * Starts a service. The future is completed when the service
     * has/was ended.
     * @param serviceName a unique service name
     * @param serviceClass class of the service to run
     * @param arg optional argument if the service expects one
     * @return a future
     */
    Future<?> start(
            String serviceName,
            Class<? extends GridService> serviceClass,
            String arg);

    <T extends GridService> T get(String serviceName);

    Future<?> stop(String serviceName);
}
