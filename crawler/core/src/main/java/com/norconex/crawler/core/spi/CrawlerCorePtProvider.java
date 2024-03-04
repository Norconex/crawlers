/* Copyright 2023 Norconex Inc.
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
import com.norconex.crawler.core.checksum.DocumentChecksummer;
import com.norconex.crawler.core.checksum.MetadataChecksummer;
import com.norconex.crawler.core.crawler.event.impl.StopCrawlerOnMaxEventListener;
import com.norconex.crawler.core.filter.DocumentFilter;
import com.norconex.crawler.core.filter.MetadataFilter;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.stop.CrawlSessionStopper;
import com.norconex.crawler.core.store.DataStoreEngine;

/**
 * <p>
 * For auto registering in {@link BeanMapper}.
 * </p>
 */
public class CrawlerCorePtProvider implements PolymorphicTypeProvider {

    @Override
    public MultiValuedMap<Class<?>, Class<?>> getPolymorphicTypes() {

        MultiValuedMap<Class<?>, Class<?>> map =
                MultiMapUtils.newListValuedHashMap();
        //NOTE: We do not register CrawlerConfig.class as a poly-type,
        // since using the crawler session-specific launcher dictates
        // with crawler confirm impl to use. If one day we make
        // all crawlers configurable in same crawl session, this is
        // where we would add it.
        addPolyType(map, CrawlSessionStopper.class);
        addPolyType(map, DataStoreEngine.class);
        addPolyType(map, DocumentChecksummer.class);
        addPolyType(map, DocumentFilter.class);
        addPolyType(map, EventListener.class, "crawler.event.impl");
        addPolyType(map, MetadataChecksummer.class);
        addPolyType(map, MetadataFilter.class);
        addPolyType(map, ReferenceFilter.class);
        addPolyType(map, SpoiledReferenceStrategizer.class);
        map.put(EventListener.class, StopCrawlerOnMaxEventListener.class);

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
