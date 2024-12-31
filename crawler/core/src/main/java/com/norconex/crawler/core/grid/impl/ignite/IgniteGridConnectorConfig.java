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
package com.norconex.crawler.core.grid.impl.ignite;

import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.ignite.Ignite;
import org.apache.ignite.configuration.IgniteConfiguration;

import com.norconex.crawler.core.grid.impl.ignite.cfg.DefaultIgniteConfigAdapter;
import com.norconex.crawler.core.grid.impl.ignite.cfg.DefaultIgniteGridActivator;
import com.norconex.crawler.core.grid.impl.ignite.cfg.LightIgniteConfig;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * <p>
 * Ignite grid connector configuration.
 * </p>
 */
@Data
@Accessors(chain = true)
public class IgniteGridConnectorConfig {

    /**
     * Ignite node configuration. When the {@link IgniteGridConnector} is used
     * without configuration, the crawler will default to a single-node local
     * cluster with minimal configuration ideal for testing but not much else.
     */
    private LightIgniteConfig igniteConfig = new LightIgniteConfig();

    /**
     * Adapter that converts the IgniteGridConfiguration into Ignite native
     * {@link IgniteConfiguration}. This is the ideal place to programmatically
     * configure Ignite and add more advanced Ignite configuration options.
     * Default is {@link DefaultIgniteConfigAdapter}.
     */
    @NonNull
    private Function<LightIgniteConfig,
            IgniteConfiguration> igniteConfigAdapter =
                    new DefaultIgniteConfigAdapter();

    /**
     * Defines the grid activation logic. Default is
     * {@link DefaultIgniteGridActivator}.
     */
    private Consumer<Ignite> igniteGridActivator =
            new DefaultIgniteGridActivator();

}
