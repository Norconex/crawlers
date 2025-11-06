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
package com.norconex.crawler.core.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;

import com.norconex.commons.lang.SystemUtil.UncheckedCallableException;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlConfig;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CoreTestUtil {

    public static final String SYS_PROP_PREFER_IPV4 =
            "java.net.preferIPv4Stack";
    public static final String SYS_PROP_PREFER_IPV6 =
            "java.net.preferIPv6Addresses";

    public static Path writeConfigToDir(
            CrawlConfig crawlConfig, Path targetDir) {
        var file = targetDir.resolve(TimeIdGenerator.next() + ".yaml");
        writeConfigToFile(crawlConfig, file);
        return file;
    }

    public static void writeConfigToFile(
            CrawlConfig crawlConfig, Path targetFile) {
        // make sure directory exists
        try {
            Files.createDirectories(targetFile.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (Writer w = Files.newBufferedWriter(
                targetFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            BeanMapper.DEFAULT.write(crawlConfig, w, Format.YAML);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T withIpv4(Callable<?> callable) {
        //NOTE: likely uneffective for the most part unless passed on
        // command-line but just in case:
        var preferIpv4 = System.getProperty(SYS_PROP_PREFER_IPV4);
        var preferIpv6 = System.getProperty(SYS_PROP_PREFER_IPV6);
        try {
            System.setProperty(SYS_PROP_PREFER_IPV4, "true");
            System.setProperty(SYS_PROP_PREFER_IPV6, "false");
            return (T) callable.call();
        } catch (Exception e) {
            throw new UncheckedCallableException(e);
        } finally {
            if (preferIpv4 != null) {
                System.setProperty(SYS_PROP_PREFER_IPV4, preferIpv4);
            }
            if (preferIpv6 != null) {
                System.setProperty(SYS_PROP_PREFER_IPV6, preferIpv6);
            }
        }
    }
}
