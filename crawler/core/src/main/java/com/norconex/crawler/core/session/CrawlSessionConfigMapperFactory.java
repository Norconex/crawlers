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
package com.norconex.crawler.core.session;

import java.util.EventListener;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.batch.queue.CommitterQueue;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.BeanMapperBuilder;
import com.norconex.crawler.core.checksum.DocumentChecksummer;
import com.norconex.crawler.core.checksum.MetadataChecksummer;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.ReferencesProvider;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.filter.DocumentFilter;
import com.norconex.crawler.core.filter.MetadataFilter;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.crawler.core.processor.DocumentProcessor;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.importer.Importer;

/**
 * Create a base {@link BeanMapperBuilder} initialized to support mapping
 * XML, Yaml, JSON to crawler objects. Concrete crawler implementations
 * may want to further initialize the created builder in their
 * own factory.
 */
public final class CrawlSessionConfigMapperFactory {

//    private static final ThreadLocal<String> crawlerDefaultsTL =
//            new ThreadLocal<>();
//    private static final ThreadLocal<BeanMapper> beanMapperTL =
//            new ThreadLocal<>();

    private CrawlSessionConfigMapperFactory() {}

    public static BeanMapper create(
            Class<? extends CrawlerConfig> crawlerConfigClass) {
        return create(crawlerConfigClass, null);
    }

    public static BeanMapper create(
            Class<? extends CrawlerConfig> crawlerConfigClass,
            Consumer<BeanMapper.BeanMapperBuilder> builderModifier) {
        var beanMapperBuilder = BeanMapper.builder()
            .ignoredProperty("crawlerDefaults")
            .unboundPropertyMapping("crawler", crawlerConfigClass)
            .unboundPropertyMapping("importer", Importer.class);
//        beanMapperBuilder.mapperBuilderCustomizer(mb -> customizeObjectMapper(
//                mb, crawlerConfigClass));


        registerPolymorpicTypes(beanMapperBuilder);

        if (builderModifier != null) {
            builderModifier.accept(beanMapperBuilder);
        }
//        handleCrawlerDefaults(beanMapperBuilder, crawlerConfigClass);

        return beanMapperBuilder.build();
    }
/*
    private static void handleCrawlerDefaults(
            BeanMapperBuilder mapperBuilder,
            Class<? extends CrawlerConfig> crawlerConfigClass) {

        // Crawler defautls
        mapperBuilder.beforeReadHandler((bm, node) -> {
            crawlerDefaultsTL.remove();
            beanMapperTL.set(bm);

            var defsNode = node.path("crawlerDefaults");
            if (defsNode != null && !(defsNode instanceof MissingNode)) {
                crawlerDefaultsTL.set(node.toString());
            }
        });
        mapperBuilder.afterReadHandler(bm -> {
            crawlerDefaultsTL.remove();
            beanMapperTL.remove();
        });
    }
*/


    private static void registerPolymorpicTypes(BeanMapperBuilder builder) {
        //MAYBE: make package configurable? Maybe use java service loaded?
        //TODO make scanning path configurable? Like java service loader?
        // or rely on fully qualified names for non Nx classes? Maybe the latter
        // is best to avoid name collisions?
        Predicate<String> predicate = nm -> nm.startsWith("com.norconex.");

        // This one has too many that are not meant to be added as configuration
        // so we only accept those that are standalone listeners:
        builder.polymorphicType(EventListener.class,
                predicate.and(nm -> nm.endsWith("EventListener")));
        builder.polymorphicType(ReferencesProvider.class, predicate);
        builder.polymorphicType(DataStoreEngine.class, predicate);
        builder.polymorphicType(ReferenceFilter.class, predicate);
        builder.polymorphicType(MetadataFilter.class, predicate);
        builder.polymorphicType(DocumentFilter.class, predicate);
        builder.polymorphicType(DocumentProcessor.class, predicate);
        builder.polymorphicType(MetadataChecksummer.class, predicate);
        builder.polymorphicType(Committer.class, predicate);
        builder.polymorphicType(CommitterQueue.class, predicate);
        builder.polymorphicType(DocumentChecksummer.class, predicate);
        builder.polymorphicType(SpoiledReferenceStrategizer.class, predicate);
        builder.polymorphicType(Fetcher.class, predicate);

        //TODO add importer dynamically somehow?  Maybe by adding
        // an unboundPropertyFactory, passing what it takes to load it?
    }

//    @SuppressWarnings({ "unchecked", "rawtypes" })
//    private static void customizeObjectMapper(
////            BeanMapper.BeanMapperBuilder beanMapperBuilder,
//            MapperBuilder<?, ?> mapperBuilder,
//            Class<? extends CrawlerConfig> crawlerConfigClass) {
//
//
//        var module = new SimpleModule();
//        module.addDeserializer((Class)crawlerConfigClass,
//                new CrawlerWithDefaultsDeserializer(crawlerConfigClass));
//        mapperBuilder.addModules(module);
//
//
//        //Since crawler defaults are only a concern when loading from
//        // configuration, maybe we should support it there instead?
//
//
//
//        // or look at Jackson InputDecorator
//        // or a custom parser just for crawler that on being created, would load the crawlerDefaults node
//        //          or it could be generic with defaultPropertyMappings: e.g.: crawlerDefault -> crawler
//
//        // or a custom DefaultDeserializationContext on jsonfactory (and overwrite readRootValue?)
//        // or subclass ObjectMapper and overwrite _readMapAndClose (or other init-like method)
//
//
//
//
//
//
//
//
////        var module = new SimpleModule();
////        module.addDeserializer((Class)crawlerConfigClass,
////                new CrawlerWithDefaultsDeserializer(crawlerConfigClass));
////        mb.addModules(module);
//
//
//
//
//        //new ObjectReader().readValue(null)
//
//
//      // handle "crawlerDefaults"
//
//
////      mp.setDefaultTyping(null)
////      mp.withCoercionConfig(getClass(), null)
////      mp.withConfigOverride(getClass(), null)
//
//
//
//
//      //TODO
//      // if encountering crawlerDefaults, stick it in thread local.
//      // at the end of mapping, if it was a crawl session config,
//      // apply default configs across.  better way?  Maybe let the
//      // loader load the defaults first, then different?
//      // or maybe just reference and load crawlerDefaults as the
//      // initial crawler before mapping?
//      // Other option: check if we can force Jackson to deserialize
//      // a specific entity first?
//
//      // maybe a custom deserializer that would look for the specific property first, then let the rest happen?
//
////      @JsonPropertyOrder({ "id", "label", "target", "source", "attributes" })
////      public class Relation { ... }
//
//      //on Jackson deserializer, context.getAttribute -> Object
//
//      //TODO
//      // can this one be used to do the reflection search?
//      //mp.subtypeResolver(null)
//    }

//    private BeanMapper getBeanMapper

//    @RequiredArgsConstructor
//    public static class CrawlerWithDefaultsDeserializer
//            extends JsonDeserializer<CrawlerConfig> {
////        private final MapperBuilder<?, ?> beanMapperBuilder;
//        private final Class<? extends CrawlerConfig> crawlerConfigClass;
////        private final BeanMapper beanMapper;
//        @Override
//        public CrawlerConfig deserialize(
//                JsonParser p, DeserializationContext ctxt) throws IOException {
//
//            var defs = crawlerDefaultsTL.get();
//            var bm = beanMapperTL.get();
//            if (defs != null) {
//
////                defsNode
//                CrawlerConfig cfg = bm.read(
//                        crawlerConfigClass,
//                        new StringReader(defs),
//                        Format.JSON);
//                return bm.read(cfg, new StringReader(ctxt.readTree(p).toString()), Format.JSON);
//            }
//
////            return p.readValueAs(crawlerConfigClass);
//
//            return  bm.read(
//                    crawlerConfigClass,
//                    new StringReader(ctxt.readTree(p).toString()),
//                    Format.JSON);
//////
//
//
//
//
//
//
//
//
//
//
//
//
////            // Apply crawler config on top of defaults, if any, and returned
////            // the bonified default as the config.
////            var parent = p.getParsingContext().getParent().getParent();
////
////System.err.println("PARENT: " + parent);
////
////            var root = p.getCodec().readTree(p);
////System.err.println("ROOT NODE: " + root);
////            var defaultsNode = root.path("crawlerDefaults");
////System.err.println("DEFAULTS NODE: " + defaultsNode);
////            if (defaultsNode != null
////                    && !(defaultsNode instanceof MissingNode)) {
////                var currentNode = ctxt.readTree(p);
////                var mergedNode = merge((JsonNode) defaultsNode, currentNode);
////                return ctxt.readTreeAsValue(mergedNode, crawlerConfigClass);
////            }
////
////            return p.readValueAs(crawlerConfigClass);
//
//
//
//
////          p.getCodec().create
////
////            node.at("/response/history").getValueAsInt();
////
////            int id = (Integer) (node.get("id")).numberValue();
////            var itemName = node.get("itemName").asText();
////
////            return new Item(id, itemName);
////            return null;
//        }
//    }



//    public static JsonNode mergeCollection(JsonNode value1, JsonNode value2){
//        ObjectNode objectNode = mapper.createObjectNode();
//
//             if(value1!=null) {
//                 ObjectNode node1 = mapper.createObjectNode();
//
//                 node1.set("value1",value1);
//                 node1.fields().forEachRemaining(kv -> objectNode.set(kv.getKey(), kv.getValue()));
//             }
//             if(value2!=null) {
//                 ObjectNode node2 = mapper.createObjectNode();
//                 node2.set("value2",value2);
//                 node2.fields().forEachRemaining(kv -> objectNode.set(kv.getKey(), kv.getValue()));
//             }
//        return objectNode;
//    }

//    public static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {
//
//        var fieldNames = updateNode.fieldNames();
//        while (fieldNames.hasNext()) {
//
//            var fieldName = fieldNames.next();
//            var jsonNode = mainNode.get(fieldName);
//            // if field exists and is an embedded object
//            if (jsonNode != null && jsonNode.isObject()) {
//                merge(jsonNode, updateNode.get(fieldName));
//            } else if (mainNode instanceof ObjectNode) {
//                // Overwrite field
//                var value = updateNode.get(fieldName);
//                ((ObjectNode) mainNode).put(fieldName, value);
//            }
//
//        }
//
//        return mainNode;
//    }

}
