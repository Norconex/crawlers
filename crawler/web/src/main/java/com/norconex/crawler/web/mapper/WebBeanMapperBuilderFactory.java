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
package com.norconex.crawler.web.mapper;

import com.norconex.commons.lang.bean.BeanMapper.BeanMapperBuilder;

/**
 * Create a base {@link BeanMapperBuilder} initialized to support mapping
 * XML, Yaml, JSON to crawler objects. Concrete crawler implementations
 * may want to further initialize the created builder in their
 * own factory.
 */
public class WebBeanMapperBuilderFactory {
  //TODO add types to service loader and delete this


//        implements Function<Class<? extends CrawlerConfig>,
//                BeanMapper.BeanMapperBuilder> {
//
//    @Override
//    public BeanMapperBuilder apply(
//            Class<? extends CrawlerConfig> crawlerConfigClass) {
//        var mapperBuilder = CrawlSessionConfigMapperFactory.create(
//                crawlerConfigClass, b ->
//
//                );
//        registerPolymorpicTypes(mapperBuilder);
//        return mapperBuilder;
//    }
//
//
//    private void registerPolymorpicTypes(BeanMapperBuilder builder) {
//        //MAYBE: make package configurable? Maybe use java service loaded?
//        Predicate<String> p = nm -> nm.startsWith("com.norconex.");
//        builder
//            .polymorphicType(WebURLNormalizer.class, p)
//            .polymorphicType(DelayResolver.class, p)
//            .polymorphicType(CanonicalLinkDetector.class, p)
//            .polymorphicType(LinkExtractor.class, p)
//            .polymorphicType(RobotsTxtProvider.class, p)
//            .polymorphicType(RobotsMetaProvider.class, p)
//            .polymorphicType(SitemapResolver.class, p)
//            .polymorphicType(SitemapLocator.class, p)
//            .polymorphicType(RecrawlableResolver.class, p);
//  }
}
