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
package com.norconex.crawler.core;

import static java.util.Optional.ofNullable;

import java.nio.file.Path;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.client.commands.Command;
import com.norconex.crawler.core.client.commands.CrawlCommand;
import com.norconex.crawler.core.util.LogUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

//TODO maybe rename to Crawler and have server side be CrawlerNode?

/**
 * <p>
 * Crawler client class.
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
public class Crawler extends CrawlerContext {

    //NOTE: this is a lightweight version of the crawler that only controls
    // and report on actual crawler instances running on nodes. It has access
    // to grid system and some monitoring and that is pretty much it.

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    private CrawlerProgressLogger progressLogger;

    Crawler(CrawlerBuilder b,
            Class<? extends CrawlerBuilderFactory> builderFactoryClass) {
        super(b, builderFactoryClass);
    }

    public static Crawler create(
            @NonNull Class<CrawlerBuilderFactory> builderFactoryClass) {
        return create(builderFactoryClass, null);
    }

    public static Crawler create(
            @NonNull Class<? extends CrawlerBuilderFactory> builderFactoryClass,
            CrawlerBuilderModifier builderModifier) {
        var factory = ClassUtil.newInstance(builderFactoryClass);
        var builder = factory.create();
        if (builderModifier != null) {
            builderModifier.modify(builder);
        }
        return new Crawler(builder, builderFactoryClass);
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
        init();
        try {
            LOG.info("Executing command: {}",
                    command.getClass().getSimpleName());
            ofNullable(getCallbacks().getBeforeCommand())
                    .ifPresent(c -> c.accept(this));
            command.execute(this);
            ofNullable(getCallbacks().getAfterCommand())
                    .ifPresent(c -> c.accept(this));
        } finally {
            close();
        }
    }

    @Override
    public void init() {
        LogUtil.logCommandIntro(LOG, getConfiguration());

        super.init();

        progressLogger = new CrawlerProgressLogger(
                getMetrics(),
                getConfiguration().getMinProgressLoggingInterval());
        progressLogger.startTracking();
    }

    @Override
    public void close() {
        progressLogger.stopTracking();
        LOG.info("Execution Summary:{}", progressLogger.getExecutionSummary());
        LOG.info("Crawler {}",
                (getState().isStopped() ? "stopped." : "completed."));
        super.close();
    }

    @Override
    public boolean isClient() {
        return true;
    }
}
