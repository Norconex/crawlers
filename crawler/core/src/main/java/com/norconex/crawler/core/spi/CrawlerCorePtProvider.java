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

import java.util.function.Predicate;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;

import com.norconex.commons.lang.ClassFinder;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.spi.PolymorphicTypeProvider;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.event.listeners.StopCrawlerOnMaxEventListener;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.grid.impl.ignite.cfg.ip.LightIgniteIpFinder;
import com.norconex.crawler.core.operations.checksum.DocumentChecksummer;
import com.norconex.crawler.core.operations.checksum.MetadataChecksummer;
import com.norconex.crawler.core.operations.filter.DocumentFilter;
import com.norconex.crawler.core.operations.filter.MetadataFilter;
import com.norconex.crawler.core.operations.filter.ReferenceFilter;
import com.norconex.crawler.core.operations.spoil.SpoiledReferenceStrategizer;

/**
 * <p>
 * For auto registering in {@link BeanMapper}.
 * </p>
 */
public class CrawlerCorePtProvider implements PolymorphicTypeProvider {

    protected static final String IGNITE_BASE_PKG = "org.apache.ignite";

    @Override
    public MultiValuedMap<Class<?>, Class<?>> getPolymorphicTypes() {
        //NOTE:
        // CrawlSessionConfig and CrawlerConfig are not registered here.
        // We leave it to crawler implementation to register them as/if
        // required. If one day we make all crawlers configurable in same
        // crawl session, this is likely where we would add it.

        MultiValuedMap<Class<?>, Class<?>> map =
                MultiMapUtils.newListValuedHashMap();
        addPolyType(map, DocumentChecksummer.class);
        addPolyType(map, DocumentFilter.class);
        addPolyType(map, EventListener.class, "event.listeners");
        addPolyType(map, GridConnector.class);
        addPolyType(map, MetadataChecksummer.class);
        addPolyType(map, MetadataFilter.class);
        addPolyType(map, ReferenceFilter.class);
        addPolyType(map, SpoiledReferenceStrategizer.class);
        map.put(EventListener.class, StopCrawlerOnMaxEventListener.class);
        addPolyType(map, LightIgniteIpFinder.class);
        return map;
    }

    private void addPolyType(
            MultiValuedMap<Class<?>, Class<?>> polyTypes,
            Class<?> baseClass) {
        addPolyType(polyTypes, baseClass, null);
    }

    private void addPolyType(
            MultiValuedMap<Class<?>, Class<?>> polyTypes,
            Class<?> baseClass,
            String corePkg) {
        polyTypes.putAll(baseClass, ClassFinder.findSubTypes(
                baseClass,
                corePkg == null
                        ? nm -> nm.startsWith(baseClass.getPackageName())
                        : filter(corePkg)));
    }

    private Predicate<String> filter(String corePkg) {
        return nm -> nm.startsWith("com.norconex.crawler.core." + corePkg);
    }
}
