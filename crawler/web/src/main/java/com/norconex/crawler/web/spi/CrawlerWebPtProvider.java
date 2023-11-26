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
package com.norconex.crawler.web.spi;

import java.util.function.Predicate;

import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;

import com.norconex.committer.core.Committer;
import com.norconex.commons.lang.ClassFinder;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.spi.PolymorphicTypeProvider;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core.checksum.MetadataChecksummer;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.filter.DocumentFilter;
import com.norconex.crawler.core.filter.MetadataFilter;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.crawler.core.processor.DocumentProcessor;
import com.norconex.crawler.web.canon.CanonicalLinkDetector;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import com.norconex.crawler.web.delay.DelayResolver;
import com.norconex.crawler.web.link.LinkExtractor;
import com.norconex.crawler.web.recrawl.RecrawlableResolver;
import com.norconex.crawler.web.robot.RobotsMetaProvider;
import com.norconex.crawler.web.robot.RobotsTxtProvider;
import com.norconex.crawler.web.sitemap.SitemapLocator;
import com.norconex.crawler.web.sitemap.SitemapResolver;
import com.norconex.crawler.web.url.WebUrlNormalizer;

/**
 * <p>
 * For auto registering in {@link BeanMapper}.
 * </p>
 */
public class CrawlerWebPtProvider implements PolymorphicTypeProvider {

    @Override
    public MultiValuedMap<Class<?>, Class<?>> getPolymorphicTypes() {
        MultiValuedMap<Class<?>, Class<?>> map =
                MultiMapUtils.newListValuedHashMap();
        addPolyType(map, CanonicalLinkDetector.class);
        addPolyType(map, MetadataChecksummer.class, "checksum.impl");
        addPolyType(map, EventListener.class, "crawler.event.impl");
        addPolyType(map, DelayResolver.class);
        addPolyType(map, Fetcher.class, "fetch.impl");
        addPolyType(map, DocumentFilter.class, "filter.impl"); //NOSONAR
        addPolyType(map, MetadataFilter.class, "filter.impl");
        addPolyType(map, ReferenceFilter.class, "filter.impl");
        addPolyType(map, LinkExtractor.class);
        addPolyType(map, DocumentProcessor.class, "processor.impl");
        addPolyType(map, RecrawlableResolver.class);
        addPolyType(map, RobotsTxtProvider.class);
        addPolyType(map, RobotsMetaProvider.class);
        addPolyType(map, SitemapLocator.class);
        addPolyType(map, SitemapResolver.class);
        addPolyType(map, WebUrlNormalizer.class);

        map.put(CrawlerConfig.class, WebCrawlerConfig.class);

        // For unit test
        addPolyType(map, EventListener.class, "session.recovery");
        addPolyType(map, Committer.class, "session.recovery");

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
        return nm -> nm.startsWith("com.norconex.crawler.web." + corePkg);
    }
}
