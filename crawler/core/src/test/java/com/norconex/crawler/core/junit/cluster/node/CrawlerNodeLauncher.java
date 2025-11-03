/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.junit.cluster.node;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.junit.WithLogLevel;
import com.norconex.crawler.core.mocks.crawler.MockCrawlDriverFactory;
import com.norconex.crawler.core.util.ExecUtil;

import lombok.Builder;
import lombok.Singular;

@Builder
public class CrawlerNodeLauncher {

    @Singular
    private final List<String> appArgs;
    @Singular
    private final List<String> jvmArgs;
    @Singular
    private final List<WithLogLevel> logLevels;
    private Class<? extends Supplier<CrawlDriver>> driverSupplierClass;
    private boolean exportEvents;
    private boolean exportCaches;

    public CrawlerNode launch(Path nodeWorkDir, Path configFile) {
        // Extract cluster root from nodeWorkDir (parent directory)
        var clusterRootDir = nodeWorkDir.getParent();

        var cmd = JvmProcess.builder()
                .mainClass(CrawlerNode.class)
                .workDir(nodeWorkDir)
                .appArgs(appArgs);
        //TODO add log4j config if not picked up automatically (it should)
        applyDebugMode(cmd);
        applyLogLevels(cmd);
        applyJvmArgs(cmd, nodeWorkDir, configFile, clusterRootDir);
        return new CrawlerNode(cmd.build().start(), nodeWorkDir);
    }

    private void applyJvmArgs(
            JvmProcess.JvmProcessBuilder cmd,
            Path nodeWorkDir,
            Path configFile,
            Path clusterRootDir) {
        cmd.jvmArgs(jvmArgs);

        // Force IPv4 and disable IPv6 to prevent JGroups IPv6 binding warnings
        cmd.jvmArg("-Djava.net.preferIPv4Stack=true");
        cmd.jvmArg("-Djava.net.preferIPv6Addresses=false");
        // Disable IPv6 completely to suppress JGroups interface enumeration warnings
        cmd.jvmArg("-Djava.net.disableIPv6=true");

        // only set config path if one is set, with at least one argument
        if (!appArgs.isEmpty() && configFile != null) {
            cmd.appArg("-config")
                    .appArg(configFile.toAbsolutePath().toString());
        }
        cmd.jvmArg(dArg(CrawlerNode.PROP_DRIVER_SUPPL,
                Optional.<Class<? extends Supplier<CrawlDriver>>>ofNullable(
                        driverSupplierClass)
                        .orElse(MockCrawlDriverFactory.class).getName()));
        cmd.jvmArg(dArg(CrawlerNode.PROP_EXPORT_EVENTS, exportEvents));
        cmd.jvmArg(dArg(CrawlerNode.PROP_EXPORT_CACHES, exportCaches));
        cmd.jvmArg(dArg(CrawlerNode.PROP_NODE_WORKDIR, nodeWorkDir));

        // Set JGroups FILE_PING location to cluster root for node discovery
        if (clusterRootDir != null) {
            var pingLocation =
                    clusterRootDir.resolve("jgroups-ping").toAbsolutePath();
            cmd.jvmArg(dArg("jgroups.ping.location", pingLocation));
        }
    }

    private void applyDebugMode(JvmProcess.JvmProcessBuilder cmd) {
        if (ExecUtil.isDebugMode()) {
            cmd.jvmArg("-agentlib:jdwp=transport=dt_socket,"
                    + "server=y,suspend=n,address=*:5005");//TODO increment ?
        }
    }

    // Add log level system properties as JVM arguments
    private void applyLogLevels(JvmProcess.JvmProcessBuilder cmd) {
        for (WithLogLevel logLevel : logLevels) {
            var level = logLevel.value();
            for (Class<?> clazz : logLevel.classes()) {
                cmd.jvmArg("-Dlog4j.logger." + clazz.getName() + "=" + level);
            }
        }
    }

    private String dArg(String key, Object value) {
        return "-D" + key + "=" + value.toString();
    }
}
