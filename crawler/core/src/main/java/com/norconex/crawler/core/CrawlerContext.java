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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.FailableRunnable;
import org.slf4j.MDC;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.metrics.CrawlerMetrics;
import com.norconex.crawler.core.metrics.CrawlerMetricsJMX;
import com.norconex.crawler.core.tasks.TaskContext;
import com.norconex.crawler.core.tasks.crawl.process.DocProcessingLedger;
import com.norconex.crawler.core.util.LogUtil;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

//TODO maybe rename to Crawler and have server side be CrawlerNode?

/**
 * <p>
 * Crawler class holding state properties required for running the crawler or
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
public class CrawlerContext implements Closeable {

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";

    // Set in constructor
    private final CrawlerConfig configuration;

    // NEEDED?
    private final Class<? extends CrawlerSpecProvider> specProviderClass;

    private final BeanMapper beanMapper;
    private final Class<? extends CrawlDocContext> docContextType;
    private final EventManager eventManager;
    private final CrawlerCallbacks callbacks;

    // Set in init()
    private Grid grid;
    private Path workDir; //TODO <-- we want to keep this? use grid store instead?
    private Path tempDir; //TODO <-- we want to keep this? use grid store instead?
    private CachedStreamFactory streamFactory;
    private DocProcessingLedger docProcessingLedger;
    private CrawlerState state;
    private CrawlerMetrics metrics;

    //    protected CrawlerContext(CrawlerSpec spec,
    //            Class<? extends CrawlerBuilderFactory> builderFactoryClass) {
    //        configuration = spec.configuration();
    //        beanMapper = spec.beanMapper();
    //        this.builderFactoryClass = builderFactoryClass;
    //        docContextType = spec.docContextType();
    //        callbacks = spec.callbacks();
    //        eventManager = new EventManager(spec.eventManager());
    //    }

    @Getter(value = AccessLevel.PROTECTED)
    private final CrawlerSpec crawlerSpec;

    private boolean initialized;

    public CrawlerContext(CrawlerContext ctx) {
        configuration = ctx.getConfiguration();
        beanMapper = ctx.getBeanMapper();
        specProviderClass = ctx.getSpecProviderClass();
        docContextType = ctx.getDocContextType();
        callbacks = ctx.getCallbacks();
        eventManager =
                ofNullable(ctx.getEventManager()).orElse(new EventManager()); // new EventManager(ctx.getEventManager());
        crawlerSpec = ctx.getCrawlerSpec();
        streamFactory = ctx.streamFactory;
        grid = ctx.grid;
        workDir = ctx.workDir;
        tempDir = ctx.tempDir;
        docProcessingLedger = ctx.docProcessingLedger;
        state = ctx.state;
        metrics = ctx.metrics;
        initialized = ctx.initialized;
    }

    public CrawlerContext(
            @NonNull Class<? extends CrawlerSpecProvider> specProviderClass,
            CrawlerConfig crawlerConfig) {
        var spec = ClassUtil.newInstance(specProviderClass).get();
        crawlerSpec = spec;
        configuration = ofNullable(crawlerConfig).orElseGet(
                () -> ClassUtil.newInstance(spec.crawlerConfigClass()));
        //        configuration = spec.configuration();

        beanMapper = spec.beanMapper();
        this.specProviderClass = specProviderClass;
        docContextType = spec.docContextType();
        callbacks = spec.callbacks();
        eventManager =
                ofNullable(spec.eventManager()).orElse(new EventManager());//new EventManager(spec.eventManager());
    }

    //    public CrawlerContext(
    //            @NonNull Class<? extends CrawlerSpecProvider> specProviderClass) {
    //        this(ClassUtil.newInstance(specProviderClass));
    //    }
    //
    //    protected CrawlerContext(@NonNull CrawlerSpecProvider specProvider) {
    //        // Make sure we can create new instances
    //        var specProvClass = specProvider.getClass();
    //        try {
    //            specProvClass.getConstructor();
    //        } catch (NoSuchMethodException e) {
    //            throw new CrawlerException("Implementation of CrawlerSpecProvider "
    //                    + "class must have a public no-argument constructor.", e);
    //        }
    //        var spec = specProvider.get();
    //        crawlerSpec = spec;
    //        configuration = spec.configuration();
    //        beanMapper = spec.beanMapper();
    //        specProviderClass = specProvClass;
    //        docContextType = spec.docContextType();
    //        callbacks = spec.callbacks();
    //        eventManager = new EventManager(spec.eventManager());
    //    }

    /**
     * Whether this code is running on a client node or server node.
     * Relevant in a clustered environment.
     * @return <code>true</code> if on a client node
     */
    public boolean isClient() {
        return true;
    }

    public void init() {
        // if this instance is already initialized, do nothing.
        if (initialized) {
            System.err.println("!! XXX Crawler context already initialized.");
            return;
        }
        System.err.println(
                "<CRAWLER_CONTEXT_INIT> // XXX Initializing Crawler context");
        initialized = true; // important to flag it early

        System.err.println("XXX LISTENERS FROM SCAN.");
        getEventManager().addListenersFromScan(getConfiguration());

        // need those? // maybe append cluster node id?
        workDir = Optional.ofNullable(getConfiguration().getWorkDir())
                .orElseGet(() -> CrawlerConfig.DEFAULT_WORKDIR)
                .resolve(FileUtil.toSafeFileName(getId()));
        tempDir = workDir.resolve("temp");
        try {
            // Will also create workdir parent:
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new CrawlerException(
                    "Could not create directory: " + tempDir, e);
        }
        streamFactory = new CachedStreamFactory(
                (int) getConfiguration().getMaxStreamCachePoolSize(),
                (int) getConfiguration().getMaxStreamCacheSize(),
                tempDir);

        docProcessingLedger = new DocProcessingLedger();
        state = new CrawlerState();
        metrics = new CrawlerMetrics();

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

        metrics.init(this);

        if (Boolean.getBoolean(TaskContext.SYS_PROP_ENABLE_JMX)) {
            CrawlerMetricsJMX.register(this);
            LOG.info("JMX support enabled.");
        } else {
            LOG.info("JMX support disabled. To enable, set -DenableJMX=true "
                    + "system property as a JVM argument.");
        }
        System.err.println(
                "</CRAWLER_CONTEXT_INIT> XXX Crawler context initialized");
    }

    @Override
    public void close() {
        System.err.println("<CRAWLER_CONTEXT_CLOSE> // XXX");
        if (Boolean.getBoolean(TaskContext.SYS_PROP_ENABLE_JMX)) {
            LOG.info("Unregistering JMX crawler MBeans.");
            safeClose(() -> CrawlerMetricsJMX.unregister(this));
        }

        safeClose(MDC::clear);
        safeClose(metrics::close);
        safeClose(eventManager::clearListeners);

        safeClose(() -> {
            if (tempDir != null) {
                try {
                    FileUtil.delete(tempDir.toFile());
                } catch (IOException e) {
                    LOG.error("Could not delete the temporary directory:"
                            + tempDir, e);
                }
            }
        });
        initialized = false;
        System.err.println("</CRAWLER_CONTEXT_CLOSE> // XXX");
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
