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

import static java.util.Optional.ofNullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;

import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.importer.handler.DocHandlerException;
import com.norconex.importer.handler.ScriptRunner;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/*
ALL Caches:

TODO: figure out which one to make configurable and mark some or all as being
  experimental?

  Some name will be given as generic names but will be mapped to actual
  more complex name.

  Use Jackson pojo mapping.

Core:
  CrawlerContext -> state management
  GLOBAL_CACHE = "global-cache";
  RUN_ONCE_CACHE = "runonce-cache";

  queue
  process
  cached

  CrawlerMetrics.eventCounts
  dedupMetadata
  dedupDocument



Web:

  SitemapRecord.class.getSimpleName();
  UrlScopeResolver.class.getSimpleName() + ".resolvedSites";

*/

@EqualsAndHashCode
@ToString
@Slf4j
public class IgniteGridConnector
        implements GridConnector,
        Configurable<IgniteGridConnectorConfig> {

    private static final String KEY_CRAWL_NODE_INDEX = "CRAWL_NODE_INDEX";
    private static final String KEY_CRAWL_SESSION_ID = "CRAWL_SESSION_ID";

    //TODO evaluate if we want those mandatory or only if not provided
    // by configurer and/or script
    public static final String CRAWL_NODE_INDEX =
            SystemUtil.getEnvironmentOrProperty(KEY_CRAWL_NODE_INDEX);
    public static final String CRAWL_SESSION_ID =
            SystemUtil.getEnvironmentOrProperty(KEY_CRAWL_SESSION_ID);

    private static final String IGNITE_BASE_DIR =
            "/ignite/data/node-%s".formatted(CRAWL_NODE_INDEX);

    @Getter
    private final IgniteGridConnectorConfig configuration =
            new IgniteGridConnectorConfig();

    @Override
    public Grid connect(
            Class<? extends CrawlerSpecProvider> crawlerSpecProviderClass,
            CrawlerConfig crawlerConfig) {

        ensureEnvVars();
        System.setProperty("IGNITE_NO_ASCII", "true");

        var cfg = new IgniteConfiguration();

        applyDefaultSettings(cfg);
        applyClassConfigurer(cfg);
        applyScriptConfigurer(cfg);
        logIgniteSpecifics(cfg);

        // Start
        var ignite = Ignition.start(cfg);
        if (configuration.getIgniteGridActivator() != null) {
            configuration.getIgniteGridActivator().activate(ignite);
        } else {
            LOG.warn("No Ignite grid activator defined.");
        }

        return new IgniteGrid(ignite);
    }

    private void applyDefaultSettings(IgniteConfiguration cfg) {
        // Generic configuration
        cfg.setWorkDirectory(IGNITE_BASE_DIR + "/work");

        // Persistent storage
        var storageCfg = new DataStorageConfiguration();
        storageCfg.setStoragePath(IGNITE_BASE_DIR + "/storage");
        storageCfg.setWalPath(IGNITE_BASE_DIR + "/wal");
        storageCfg.setWalArchivePath(IGNITE_BASE_DIR + "/wal/archive");

        // Data reagion
        var dataRegionCfg = new DataRegionConfiguration();
        dataRegionCfg.setName("Default_Region");
        dataRegionCfg.setPersistenceEnabled(true);
        storageCfg.setDefaultDataRegionConfiguration(dataRegionCfg);

        cfg.setDataStorageConfiguration(storageCfg);
    }

    private void applyClassConfigurer(IgniteConfiguration cfg) {
        if (configuration.getConfigurer() != null) {
            configuration.getConfigurer().configure(cfg);
        }
    }

    private void applyScriptConfigurer(IgniteConfiguration cfg) {
        if (StringUtils.isNotBlank(configuration.getConfigurerScript())) {
            try {
                new ScriptRunner<>(StringUtils.defaultIfBlank(
                        configuration.getConfigurerScriptEngine(),
                        ScriptRunner.JAVASCRIPT_ENGINE),
                        configuration.getConfigurerScript()).eval(b -> {
                            b.put("cfg", cfg);
                            b.put("nodeIndex", CRAWL_NODE_INDEX);
                            b.put("sessionId", CRAWL_SESSION_ID);
                        });
            } catch (DocHandlerException e) {
                throw new GridException(
                        "Could not configure Ignite via scripting.", e);
            }
        }
    }

    private void logIgniteSpecifics(IgniteConfiguration cfg) {
        var storageCfg = cfg.getDataStorageConfiguration();
        LOG.info("Crawl session id: {}", CRAWL_SESSION_ID);
        LOG.info("""
        Ignite specifics:
            Node index:       %s
            Work path:        %s
            Storage path:     %s
            WAL path:         %s
            WAL archive path: %s
        """.formatted(
                CRAWL_NODE_INDEX,
                cfg.getWorkDirectory(),
                ofNullable(storageCfg.getStoragePath()).orElse("N/A"),
                ofNullable(storageCfg.getWalPath()).orElse("N/A"),
                ofNullable(storageCfg.getWalArchivePath()).orElse("N/A")));
    }

    private void ensureEnvVars() {
        if (StringUtils.isBlank(CRAWL_NODE_INDEX)) {
            throw new GridException("""
                    Missing environment variable (or system property): "%s".
                    Needs to be a value unique to each node and the same on
                    each crawl session.
                    """.formatted(KEY_CRAWL_NODE_INDEX));
        }
        if (StringUtils.isBlank(CRAWL_SESSION_ID)) {
            throw new GridException("""
                    Missing environment variable (or system property): "%s".
                    Needs to be a value unique to each crawl session but the
                    same across all nodes during the session.
                    """.formatted(KEY_CRAWL_SESSION_ID));
        }
    }

}
