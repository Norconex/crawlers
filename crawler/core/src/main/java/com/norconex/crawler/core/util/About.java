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
package com.norconex.crawler.core.util;

import static org.apache.commons.lang3.StringUtils.removeEndIgnoreCase;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.SystemUtils;

import com.norconex.committer.core.Committer;
import com.norconex.commons.lang.PackageManifest;
import com.norconex.crawler.core.CrawlerConfig;

import lombok.NonNull;

public final class About {
    /** Simple ASCI art of Norconex. */
    public static final String NORCONEX_ASCII = """
             _   _  ___  ____   ____ ___  _   _ _______  __
            | \\ | |/ _ \\|  _ \\ / ___/ _ \\| \\ | | ____\\ \\/ /
            |  \\| | | | | |_) | |  | | | |  \\| |  _|  \\  /\s
            | |\\  | |_| |  _ <| |__| |_| | |\\  | |___ /  \\\s
            |_| \\_|\\___/|_| \\_\\\\____\\___/|_| \\_|_____/_/\\_\\

            ================ C R A W L E R ================
            """;

    private About() {
    }

    public static String about(
            @NonNull CrawlerConfig config, boolean extended) {
        try (var sw = new StringWriter(); var w = new PrintWriter(sw, true)) {

            w.println(NORCONEX_ASCII);

            w.println("Version:\n  " + releaseVersion(config.getClass()));

            if (extended) {
                // committer
                var committerClasses = configuredCommitters(config);
                w.println("Committers:");
                if (CollectionUtils.isNotEmpty(committerClasses)) {
                    for (Class<?> cls : committerClasses) {
                        w.println("  " + committerName(cls));
                    }
                } else {
                    w.println("  <None>");
                }
            }

            // runtime
            w.println("Runtime:");
            w.println("  Name:      " + SystemUtils.JAVA_RUNTIME_NAME);
            w.println("  Version:   " + SystemUtils.JAVA_RUNTIME_VERSION);
            w.println("  Vendor:    " + SystemUtils.JAVA_VENDOR);
            return sw.toString();
        } catch (IOException e) {
            // Should not happen: StringWriter does not throw.
            throw new UncheckedIOException(e);
        }
    }

    private static String committerName(Class<?> cls) {
        return "%s (%s)".formatted(
                removeEndIgnoreCase(cls.getSimpleName(), "Committer"),
                PackageManifest.of(cls).getTitle());
    }

    private static String releaseVersion(Class<?> cls) {
        var manifest = PackageManifest.of(cls);
        return manifest.getTitle() + " " + manifest.getVersion();
    }

    private static Set<Class<?>> configuredCommitters(CrawlerConfig config) {
        return config
                .getCommitters()
                .stream()
                .map(Committer::getClass)
                .collect(Collectors.toSet());
    }
}
