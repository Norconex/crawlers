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
package com.norconex.crawler.fs.spi;

import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;

import com.norconex.commons.lang.ClassFinder;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.spi.PolymorphicTypeProvider;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.operations.checksum.MetadataChecksummer;
import com.norconex.crawler.fs.fetch.impl.cmis.CmisFetcher;
import com.norconex.crawler.fs.fetch.impl.ftp.FtpFetcher;
import com.norconex.crawler.fs.fetch.impl.hdfs.HdfsFetcher;
import com.norconex.crawler.fs.fetch.impl.local.LocalFetcher;
import com.norconex.crawler.fs.fetch.impl.sftp.SftpFetcher;
import com.norconex.crawler.fs.fetch.impl.smb.SmbFetcher;
import com.norconex.crawler.fs.fetch.impl.webdav.WebDavFetcher;
import com.norconex.crawler.fs.operations.checksum.FsMetadataChecksummer;

/**
 * <p>
 * For auto registering in {@link BeanMapper}.
 * </p>
 */
public class CrawlerFsPtProvider implements PolymorphicTypeProvider {

    @Override
    public MultiValuedMap<Class<?>, Class<?>> getPolymorphicTypes() {
        MultiValuedMap<Class<?>, Class<?>> map =
                MultiMapUtils.newListValuedHashMap();

        addPolyType(map, MetadataChecksummer.class, "cmd.crawl.operations");
        map.putAll(
                Fetcher.class, List.of(
                        FsMetadataChecksummer.class,
                        CmisFetcher.class,
                        FtpFetcher.class,
                        HdfsFetcher.class,
                        LocalFetcher.class,
                        SftpFetcher.class,
                        SmbFetcher.class,
                        WebDavFetcher.class));
        return map;
    }

    static void addPolyType(
            MultiValuedMap<Class<?>, Class<?>> polyTypes,
            Class<?> baseClass,
            String corePkg) {
        polyTypes.putAll(baseClass, ClassFinder.findSubTypes(
                baseClass,
                corePkg == null
                        ? nm -> nm.startsWith(baseClass.getPackageName())
                        : filter(corePkg)));
    }

    private static Predicate<String> filter(String corePkg) {
        return nm -> nm.startsWith("com.norconex.crawler.fs." + corePkg);
    }
}
