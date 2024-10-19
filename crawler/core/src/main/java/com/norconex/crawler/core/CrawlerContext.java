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

import java.io.Closeable;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.FailableRunnable;
import org.slf4j.MDC;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.process.DocProcessingLedger;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.metrics.CrawlerMetrics;
import com.norconex.crawler.core.metrics.CrawlerMetricsJMX;
import com.norconex.crawler.core.tasks.CrawlerTaskContext;
import com.norconex.crawler.core.util.LogUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

//TODO maybe rename to Crawler and have server side be CrawlerNode?

/**
 * <p>
 * Base crawler class holding contextual properties for running the crawler or
 * crawler tasks.
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
 */
@EqualsAndHashCode
@Getter
@Slf4j
public abstract class CrawlerContext implements Closeable {

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    // Set in constructor
    private final CrawlerConfig configuration;
    private final Class<? extends CrawlerBuilderFactory> builderFactoryClass;
    private final BeanMapper beanMapper;
    private final Class<? extends CrawlDocContext> docContextType;
    private final EventManager eventManager;

    // Set in init()
    private Grid grid;

    // Others
    private final DocProcessingLedger docProcessingLedger =
            new DocProcessingLedger();
    private final CrawlerState state = new CrawlerState();
    private CrawlerMetrics metrics = new CrawlerMetrics();

    protected CrawlerContext(CrawlerBuilder b,
            Class<? extends CrawlerBuilderFactory> builderFactoryClass) {
        configuration = b.configuration();
        beanMapper = b.beanMapper();
        this.builderFactoryClass = builderFactoryClass;
        docContextType = b.docContextType();
        eventManager = new EventManager(b.eventManager());
    }

    public abstract boolean isClient();

    public void init() {
        // NOTE: order matters
        grid = configuration.getGridConnector().connect(this);
        state.init(this);
        docProcessingLedger.init(this);

        //--- Ensure good state/config ---
        if (StringUtils.isBlank(getId())) {
            throw new CrawlerException(
                    "Crawler must be given a unique identifier (id).");
        }
        LogUtil.setMdcCrawlerId(getId());
        Thread.currentThread().setName(getId());

        getEventManager().addListenersFromScan(getConfiguration());

        metrics.init(this);

        if (Boolean.getBoolean(CrawlerTaskContext.SYS_PROP_ENABLE_JMX)) {
            CrawlerMetricsJMX.register(this);
            LOG.info("JMX support enabled.");
        } else {
            LOG.info("JMX support disabled. To enable, set -DenableJMX=true "
                    + "system property as a JVM argument.");
        }
    }

    @Override
    public void close() {
        if (Boolean.getBoolean(CrawlerTaskContext.SYS_PROP_ENABLE_JMX)) {
            LOG.info("Unregistering JMX crawler MBeans.");
            safeClose(() -> CrawlerMetricsJMX.unregister(this));
        }

        safeClose(MDC::clear);
        safeClose(metrics::close);
        safeClose(eventManager::clearListeners);
    }

    //TODO document they do not cross over nodes
    public void fire(Event event) {
        getEventManager().fire(event);
    }

    public void fire(String eventName) {
        fire(CrawlerEvent.builder().name(eventName).source(this).build());
    }

    public void fire(String eventName, Object subject) {
        fire(CrawlerEvent.builder()
                .name(eventName)
                .source(this)
                .subject(subject)
                .build());
    }

    public String getId() {
        return getConfiguration().getId();
    }

    @Override
    public String toString() {
        return getId();
    }

    /**
     * Ensures that any exceptions thrown while closing does not impact
     * the rest of the flow.
     * @param runnable
     */
    protected void safeClose(FailableRunnable<Exception> runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            LOG.error("Could not close resource.", e);
        }
    }
}
