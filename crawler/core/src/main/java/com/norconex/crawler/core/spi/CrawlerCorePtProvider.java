/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.core.spi;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.spi.BasePolymorphicTypeProvider;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.doc.operations.checksum.DocumentChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.MetadataChecksummer;
import com.norconex.crawler.core.doc.operations.filter.DocumentFilter;
import com.norconex.crawler.core.doc.operations.filter.MetadataFilter;
import com.norconex.crawler.core.doc.operations.filter.ReferenceFilter;
import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategizer;

/**
 * <p>
 * For auto registering in {@link BeanMapper}.
 * </p>
 */
public class CrawlerCorePtProvider extends BasePolymorphicTypeProvider {

    protected static final String BASE_PKG = "com.norconex.crawler.core.";

    @Override
    protected void register(Registry registry) {
        registry
                .addFromScan(DocumentChecksummer.class)
                .addFromScan(DocumentFilter.class)
                .addFromScan(EventListener.class, BASE_PKG + "event.listeners")
                .addFromScan(MetadataChecksummer.class)
                .addFromScan(MetadataFilter.class)
                .addFromScan(ReferenceFilter.class)
                .addFromScan(SpoiledReferenceStrategizer.class);
    }
}
