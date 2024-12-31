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

import static java.util.Optional.ofNullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.grid.impl.ignite.cfg.DefaultIgniteConfigAdapter;
import com.norconex.crawler.core.grid.impl.ignite.cfg.DefaultIgniteGridActivator;
import com.norconex.crawler.core.util.ConfigUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@ToString
@Slf4j
public class IgniteGridConnector
        implements GridConnector,
        Configurable<IgniteGridConnectorConfig> {

    @Getter
    private final IgniteGridConnectorConfig configuration =
            new IgniteGridConnectorConfig();

    @Override
    public Grid connect(
            Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass,
            CrawlerConfig crawlerConfig) {

        IgniteConfiguration igniteCfg;
        if (new IgniteGridConnectorConfig().equals(configuration)) {
            LOG.warn("""
                Using Ignite without configuration. Using default \
                single-node configuration for development/testing \
                purposes. Do not use in production.""");
            igniteCfg = IgniteGridDefaultConfig.get(crawlerConfig);
        } else {
            igniteCfg = ofNullable(configuration.getIgniteConfigAdapter())
                    .orElseGet(DefaultIgniteConfigAdapter::new)
                    .apply(configuration.getIgniteConfig());
        }

        if (StringUtils.isBlank(igniteCfg.getWorkDirectory())) {
            igniteCfg.setWorkDirectory(ConfigUtil.resolveWorkDir(crawlerConfig)
                    .resolve("ignite").toAbsolutePath().toString());
            LOG.info("Ignite %s work directory: %s"
                    .formatted(igniteCfg.getIgniteInstanceName(),
                            igniteCfg.getWorkDirectory()));
        }

        var ignite = Ignition.getOrStart(igniteCfg);

        ofNullable(configuration.getIgniteGridActivator())
                .orElseGet(DefaultIgniteGridActivator::new).accept(ignite);

        return new IgniteGrid(ignite);
    }
}
