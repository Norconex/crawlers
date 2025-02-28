/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.grid.impl.ignite;

import com.norconex.crawler.core.grid.impl.ignite.activator.DefaultIgniteGridActivator;
import com.norconex.crawler.core.grid.impl.ignite.activator.IgniteGridActivator;
import com.norconex.crawler.core.grid.impl.ignite.configurer.IgniteConfigurer;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Ignite grid connector configuration. Native IgniteConfiguration can be
 * configured with an {@link #setConfigurer(IgniteConfigurer)} and/or with
 * {@link #setConfigurerScript(String)}. If both are be provided,
 * the "configurer" runs before the "configurerScript".
 * </p>
 */
@Data
@Accessors(chain = true)
public class IgniteGridConnectorConfig {

    private IgniteConfigurer configurer;

    private String configurerScriptEngine;
    private String configurerScript;

    /**
     * Defines the grid activation logic. Default is
     * {@link DefaultIgniteGridActivator}.
     */
    private IgniteGridActivator igniteGridActivator =
            new DefaultIgniteGridActivator();
}
