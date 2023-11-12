/* Copyright 2017-2023 Norconex Inc.
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
package com.norconex.crawler.web.crawler;

class WebCrawlerConfigTest {

  //TODO migrate config
//    @Test
//    void testWebCrawlerConfig() {
//        assertThatNoException().isThrownBy(() ->
//                Web.beanMapper().assertWriteRead(
//                        WebStubber.crawlerConfigRandom(), Format.XML));
//    }

//TODO migrate config
//    @Test
//    void testValidation() throws IOException {
//        assertThatNoException().isThrownBy(() -> {
//                try (Reader r = new InputStreamReader(
//                        getClass().getResourceAsStream(
//                                "/validation/web-crawl-session-full.xml"))) {
//                    var cfg = new CrawlSessionConfig();
//                    new XML(r).populate(cfg);
//                    XML.assertWriteRead(cfg, "crawlSession");
//                }
//            }
//        );
//    }


//TODO migrate this:
    // Test for: https://github.com/Norconex/collector-http/issues/326
//    @Test
//    void testCrawlerDefaults() throws IOException {
//        var config = new CrawlSessionConfig();
//        BeanMapper.DEFAULT.read(
//                config, ResourceLoader.getXmlReader(getClass()), Format.XML);
//        Assertions.assertEquals(2, config.getCrawlerConfigs().size());
//
//        // Make sure crawler defaults are applied properly.
//        var cc1 = (WebCrawlerConfig) config.getCrawlerConfigs().get(0);
//        Assertions.assertFalse(
//                cc1.getUrlCrawlScopeStrategy().isStayOnDomain(),
//                "stayOnDomain 1 must be false");
//        Assertions.assertFalse(
//                cc1.getUrlCrawlScopeStrategy().isStayOnPort(),
//                "stayOnPort 1 must be false");
//        Assertions.assertTrue(
//                cc1.getUrlCrawlScopeStrategy().isStayOnProtocol(),
//                "stayOnProtocol 1 must be true");
//
//        var cc2 = (WebCrawlerConfig) config.getCrawlerConfigs().get(1);
//        Assertions.assertTrue(
//                cc2.getUrlCrawlScopeStrategy().isStayOnDomain(),
//                "stayOnDomain 2 must be true");
//        Assertions.assertTrue(
//                cc2.getUrlCrawlScopeStrategy().isStayOnPort(),
//                "stayOnPort 2 must be true");
//        Assertions.assertTrue(
//                cc2.getUrlCrawlScopeStrategy().isStayOnProtocol(),
//                "stayOnProtocol 2 must be true");
//    }
}
