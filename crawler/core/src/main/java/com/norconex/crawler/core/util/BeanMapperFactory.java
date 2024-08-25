/* Copyright 2023-2024 Norconex Inc.
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

import java.util.EventListener;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.batch.queue.CommitterQueue;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.BeanMapperBuilder;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.doc.operations.DocumentConsumer;
import com.norconex.crawler.core.doc.operations.checksum.DocumentChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.MetadataChecksummer;
import com.norconex.crawler.core.doc.operations.filter.DocumentFilter;
import com.norconex.crawler.core.doc.operations.filter.MetadataFilter;
import com.norconex.crawler.core.doc.operations.filter.ReferenceFilter;
import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.doc.pipelines.queue.ReferencesProvider;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.importer.Importer;

/**
 * Create a base {@link BeanMapperBuilder} initialized to support mapping
 * XML, Yaml, JSON to crawler objects. Concrete crawler implementations
 * may want to further initialize the created builder in their
 * own factory.
 */
public final class BeanMapperFactory {

    private BeanMapperFactory() {
    }

    public static BeanMapper create(
            Class<? extends CrawlerConfig> crawlerConfigClass
    ) {
        return create(crawlerConfigClass, null);
    }

    public static BeanMapper create(
            Class<? extends CrawlerConfig> crawlerConfigClass,
            Consumer<BeanMapper.BeanMapperBuilder> builderModifier
    ) {
        var beanMapperBuilder = BeanMapper.builder()
                .ignoredProperty("crawlerDefaults")
                .defaultPolymorphicType(CrawlerConfig.class, crawlerConfigClass)
                .unboundPropertyMapping("crawler", crawlerConfigClass)
                .unboundPropertyMapping("importer", Importer.class);

        registerPolymorpicTypes(beanMapperBuilder);

        if (builderModifier != null) {
            builderModifier.accept(beanMapperBuilder);
        }

        return beanMapperBuilder.build();
    }

    private static void registerPolymorpicTypes(BeanMapperBuilder builder) {
        //MAYBE: make package configurable? Maybe use java service loaded?
        //TODO make scanning path configurable? Like java service loader?
        // or rely on fully qualified names for non Nx classes? Maybe the latter
        // is best to avoid name collisions?
        Predicate<String> predicate = nm -> nm.startsWith("com.norconex.");

        // This one has too many that are not meant to be added as configuration
        // so we only accept those that are standalone listeners:
        builder.polymorphicType(
                EventListener.class,
                predicate.and(nm -> nm.endsWith("EventListener"))
        );
        builder.polymorphicType(ReferencesProvider.class, predicate);
        builder.polymorphicType(DataStoreEngine.class, predicate);
        builder.polymorphicType(ReferenceFilter.class, predicate);
        builder.polymorphicType(MetadataFilter.class, predicate);
        builder.polymorphicType(DocumentFilter.class, predicate);
        builder.polymorphicType(DocumentConsumer.class, predicate);
        builder.polymorphicType(MetadataChecksummer.class, predicate);
        builder.polymorphicType(Committer.class, predicate);
        builder.polymorphicType(CommitterQueue.class, predicate);
        builder.polymorphicType(DocumentChecksummer.class, predicate);
        builder.polymorphicType(SpoiledReferenceStrategizer.class, predicate);
        builder.polymorphicType(Fetcher.class, predicate);

        //TODO add importer dynamically somehow?  Maybe by adding
        // an unboundPropertyFactory, passing what it takes to load it?
    }
}
