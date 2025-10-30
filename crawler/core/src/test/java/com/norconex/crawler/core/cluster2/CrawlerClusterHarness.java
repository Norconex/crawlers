package com.norconex.crawler.core.cluster2;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;

import com.healthmarketscience.jackcess.RuntimeIOException;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.core.junit.WithLogLevel;
import com.norconex.crawler.core.junit.crawler.CachesExporterCrawlDriverWrapper;
import com.norconex.crawler.core.junit.crawler.ClusteredCrawlOuput;
import com.norconex.crawler.core.mocks.cli.MockCliEventWriter;
import com.norconex.crawler.core.util.ExecUtil;

import lombok.Generated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CrawlerClusterHarness {

    private final List<Process> nodeProcesses = new ArrayList<>();
    // Nodes may be added/removed so we use a separate counter to avoid overlaps
    // Used in creating unique workDirs
    private final AtomicInteger nodeCounter = new AtomicInteger();
    // Number of clusters created within this JVM, to avoid collision
    // Used in creating unique crawler id
    private final AtomicInteger clusterCounter = new AtomicInteger();

    //--- This JVM -------------------------------------------------------------

    // async (so we can add later)
    public List<Process> launch(int numOfNodes,
            CrawlerClusterLaunchParams req) {
        List<Process> processes = new ArrayList<>();

        for (var i = 0; i < numOfNodes; i++) {
            processes.add(launchNodeJvm(nodeCounter.incrementAndGet(), req));
        }
        //TODO wait for nodes to be added
        nodeProcesses.addAll(processes);
        return processes;
    }

    public ClusteredCrawlOuput waitForTermination() {
        //TODO load node data and leave deletion out to Junit tempdir
        return null;
    }

    private Process launchNodeJvm(
            int nodeIndex, CrawlerClusterLaunchParams request) {
        var cmd = JvmProcess.builder()
                .mainClass(CrawlerClusterHarness.class)
                .jvmArgs(request.getJvmArgs())
                .appArgs(request.getAppArgs());

        if (ExecUtil.isDebugMode()) {
            cmd.jvmArg("-agentlib:jdwp=transport=dt_socket,"
                    + "server=y,suspend=n,address=*:5005");
        }

        //TODO add log4j config if not picked up automatically (it should)

        // Add log level system properties as JVM arguments
        for (WithLogLevel logLevel : request.getLogLevels()) {
            var level = logLevel.value();
            for (Class<?> clazz : logLevel.classes()) {
                cmd.jvmArg("-Dlog4j.logger." + clazz.getName() + "=" + level);
            }
        }

        var cfg = request.getCrawlConfig();
        if (cfg != null) {
            if (StringUtils.isBlank(cfg.getId())) {
                cfg.setId("cluster-" + clusterCounter.incrementAndGet());
            }
            var workDir = request.getClusterRootDir().resolve(
                    "node-" + nodeIndex);
            cfg.setWorkDir(workDir);
            cmd.workDir(request.getClusterRootDir());

            addEventWriter(cfg);

            // only set config path if a config object was passed and at
            // lease one argument
            if (!request.getAppArgs().isEmpty()) {
                var cfgFile = workDir.resolve("config.yaml");
                writeConfigToDisk(cfg, cfgFile);
                cmd.appArg("-config").appArg(cfgFile.toString());
            }
        }

        cmd.jvmArg("-Dtest.driverSupplier=" + request.getDriverSupplierClass());
        return cmd.build().start();
    }

    private void writeConfigToDisk(CrawlConfig config, Path file) {
        try (Writer w = Files.newBufferedWriter(
                file, StandardOpenOption.CREATE_NEW)) {
            BeanMapper.DEFAULT.write(config, w, Format.YAML);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    private void addEventWriter(CrawlConfig cfg) {
        var eventFile = cfg.getWorkDir().resolve(
                TimeIdGenerator.next() + "-events.txt");
        if (cfg.getEventListeners().stream()
                .noneMatch(MockCliEventWriter.class::isInstance)) {
            var eventWriter = new MockCliEventWriter();
            eventWriter.setEventFile(eventFile);
            cfg.addEventListener(eventWriter);
        }
    }

    // wait for completion -> ClusteredCrawlOuput

    //--- Other JVM ------------------------------------------------------------

    @SuppressWarnings("unchecked")
    @Generated // excluded from coverage
    public static void main(String[] args) {
        var driverSupp = System.getProperty("test.driverSupplier");
        CrawlDriver driver = null;
        try {
            driver = ((Supplier<CrawlDriver>) Class
                    .forName(driverSupp)
                    .getDeclaredConstructor()
                    .newInstance()).get();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid crawl driver class: " + driverSupp);
        }
        try {
            LOG.info("Received launch arguments: " + String.join(" ", args));
            // Explicitly exit to terminate the JVM
            System.exit(CliCrawlerLauncher.launch(
                    CachesExporterCrawlDriverWrapper.wrap(driver), args));
        } catch (Exception e) {
            e.printStackTrace();
            // Exit with error code on failure
            System.exit(1);
        }
    }
}
