/* Copyright 2022-2023 Norconex Inc.
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
package com.norconex.crawler.core.session;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import com.norconex.commons.lang.PackageManifest;

/**
 * Utility methods related to crawl sessions.
 * Not part of public API.
 */
final class CrawlSessionUtil {

    private CrawlSessionUtil() {}

    public static String getReleaseInfo(CrawlSessionConfig sessionConfig) {
        try (var sw = new StringWriter();
                var w = new PrintWriter(sw, true)) {
            w.println(releaseVersion("Crawler", CrawlSessionUtil.class));
            //TODO maybe not needed if we also bring committers into same repo
            var committerClasses = classpathCommitters(sessionConfig);
            w.println("Committers:");
            if (CollectionUtils.isNotEmpty(committerClasses)) {
                committerClasses.forEach(cls ->
                    w.println(releaseVersion("  " + StringUtils.removeEndIgnoreCase(
                            cls.getSimpleName(), "Committer"), cls)));
            } else {
                w.println("  <None>");
            }

            w.println("Runtime:");
            w.println("  Name:             " + SystemUtils.JAVA_RUNTIME_NAME);
            w.println("  Version:          " + SystemUtils.JAVA_RUNTIME_VERSION);
            w.println("  Vendor:           " + SystemUtils.JAVA_VENDOR);
            return sw.toString();
        } catch (IOException e) {
            // Should not happen: StringWriter does not throw.
            throw new UncheckedIOException(e);
        }
    }
    private static String releaseVersion(String moduleName, Class<?> cls) {
        var manifest = PackageManifest.of(cls);
        return labelValue(
                moduleName, manifest.getTitle() + " " + manifest.getVersion());
    }
    private static String labelValue(String label, String value) {
        return StringUtils.rightPad(label + ": ", 20, ' ') + value;
    }

    private static Set<Class<?>> classpathCommitters(
            CrawlSessionConfig sessionConfig) {
        Set<Class<?>> classes = new HashSet<>();
        if (sessionConfig == null) {
            return classes;
        }
        sessionConfig.getCrawlerConfigs().forEach(
                crawlerConfig -> crawlerConfig.getCommitters().forEach(
                        committer -> classes.add(committer.getClass())));
        return classes;
    }
}
