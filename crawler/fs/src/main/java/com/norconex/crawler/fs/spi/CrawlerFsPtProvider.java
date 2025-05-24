/* Copyright 2024-2025 Norconex Inc.
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

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.spi.BasePolymorphicTypeProvider;
import com.norconex.crawler.core.doc.operations.checksum.MetadataChecksummer;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.fs.fetch.impl.cmis.CmisFetcher;
import com.norconex.crawler.fs.fetch.impl.ftp.FtpFetcher;
import com.norconex.crawler.fs.fetch.impl.hdfs.HdfsFetcher;
import com.norconex.crawler.fs.fetch.impl.local.LocalFetcher;
import com.norconex.crawler.fs.fetch.impl.sftp.SftpFetcher;
import com.norconex.crawler.fs.fetch.impl.smb.SmbFetcher;
import com.norconex.crawler.fs.fetch.impl.webdav.WebDavFetcher;

/**
 * <p>
 * For auto registering in {@link BeanMapper}.
 * </p>
 */
public class CrawlerFsPtProvider extends BasePolymorphicTypeProvider {

    protected static final String BASE_PKG = "com.norconex.crawler.fs.";

    @Override
    protected void register(Registry registry) {
        registry
                .addFromScan(
                        MetadataChecksummer.class,
                        BASE_PKG + "doc.operations")
                .add(Fetcher.class,
                        CmisFetcher.class,
                        FtpFetcher.class,
                        HdfsFetcher.class,
                        LocalFetcher.class,
                        SftpFetcher.class,
                        SmbFetcher.class,
                        WebDavFetcher.class);
    }
}
