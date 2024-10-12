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
package com.norconex.crawler.core.client;

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.core.CrawlerBuilder;
import com.norconex.crawler.core.CrawlerBuilderFactory;
import com.norconex.crawler.core.CrawlerBuilderModifier;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.CrawlerState;
import com.norconex.crawler.core.client.commands.Command;
import com.norconex.crawler.core.client.commands.CrawlCommand;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.util.LogUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

//TODO maybe rename to Crawler and have server side be CrawlerNode?

/**
 * <p>
 * Crawler base class.
 * </p>
 * <h3>JMX Support</h3>
 * <p>
 * JMX support is disabled by default. To enable it, set the system property
 * "enableJMX" to <code>true</code>. You can do so by adding this to your Java
 * launch command:
 * </p>
 *
 * <pre>
 *     -DenableJMX=true
 * </pre>
 *
 * @see CrawlerConfig
 */
@Slf4j
@EqualsAndHashCode
@Getter
public class CrawlerClient {

    //TODO a light weight version of the crawler that only controls
    // and report on actual crawler instances running on nodes.
    // has access to gridsystem and some monitoring and that is pretty much it.

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    private final CrawlerConfig configuration;
    private final Grid grid;
    private final Class<? extends CrawlerBuilderFactory> builderFactoryClass;
    private final BeanMapper beanMapper;
    private final CrawlerState state = new CrawlerState();

    CrawlerClient(CrawlerBuilder b,
            Class<? extends CrawlerBuilderFactory> builderFactoryClass) {
        configuration = b.configuration();
        beanMapper = b.beanMapper();
        this.builderFactoryClass = builderFactoryClass;
        grid = configuration.getGridConnector().connect(this);
        state.init(grid);
    }

    public static CrawlerClient create(
            @NonNull Class<CrawlerBuilderFactory> builderFactoryClass) {
        return create(builderFactoryClass, null);
    }

    public static CrawlerClient create(
            @NonNull Class<? extends CrawlerBuilderFactory> builderFactoryClass,
            CrawlerBuilderModifier builderModifier) {
        var factory = ClassUtil.newInstance(builderFactoryClass);
        var builder = factory.create();
        if (builderModifier != null) {
            builderModifier.modify(builder);
        }
        return new CrawlerClient(builder, builderFactoryClass);
    }

    public void crawl() {
        executeCommand(new CrawlCommand());
    }

    public void stop() {
        //TODO implement me
    }

    public void clean() {
        //TODO implement me
    }

    public void cacheExport(Path dir) {
        //TODO implement me
    }

    public void cacheImport(Path inFile) {
        //TODO implement me
    }

    private void executeCommand(Command command) {
        //--- Ensure good state/config ---
        if (StringUtils.isBlank(configuration.getId())) {
            throw new CrawlerException(
                    "Crawler must be given a unique identifier (id).");
        }

        //TODO some monitoring here to report progress on client node

        LogUtil.setMdcCrawlerId(configuration.getId());

        LogUtil.logCommandIntro(LOG, configuration);

        //        try (var grid = getGridSystem()) {
        //            grid.init(this);
        //        gridSystem.clientInit(null);
        //        init(true);
        try {
            LOG.info("Executing command: {}",
                    command.getClass().getSimpleName());
            command.execute(this);
        } finally {
            //            orderlyShutdown(true);
        }
        grid.close();
        //        }
    }
}
