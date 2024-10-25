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
package com.norconex.crawler.web.callbacks;

import java.util.function.Consumer;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.web.doc.operations.scope.impl.GenericUrlScopeResolver;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

/**
 * Web crawler-specific initialization before the crawler starts.
 */
@Slf4j
class BeforeWebCommand implements Consumer<CrawlerContext> {

    @Override
    public void accept(CrawlerContext crawlerContext) {
        var cfg = Web.config(crawlerContext);
        var scope = "";
        if (cfg.getUrlScopeResolver() instanceof GenericUrlScopeResolver res) {
            var scopeCfg = res.getConfiguration();
            scope = """
                    Crawl scope boundaries:
                      Domain:           %s
                      Sub-domain:       %s
                      Protocol:         %s
                      Port:             %s
                      Sitemap:          %s""".formatted(
                    yn(scopeCfg.isStayOnDomain()),
                    yn(scopeCfg.isIncludeSubdomains()),
                    yn(scopeCfg.isStayOnProtocol()),
                    yn(scopeCfg.isStayOnPort()),
                    yn(scopeCfg.isStayOnSitemap()));
        }

        LOG.info("""
                Enabled features:

                RobotsTxt:          %s
                RobotsMeta:         %s
                Sitemap discovery:  %s
                Sitemap resolution: %s
                Canonical links:    %s
                Metadata:
                  Checksummer:      %s
                  Deduplication:    %s
                Document:
                  Checksummer:      %s
                  Deduplication:    %s
                %s
                """.formatted(
                yn(cfg.getRobotsTxtProvider() != null),
                yn(cfg.getRobotsMetaProvider() != null),
                yn(cfg.getSitemapLocator() != null),
                yn(cfg.getSitemapResolver() != null),
                yn(cfg.getCanonicalLinkDetector() != null),
                yn(cfg.getMetadataChecksummer() != null),
                yn(cfg.isMetadataDeduplicate()
                        && cfg.getMetadataChecksummer() != null),
                yn(cfg.getDocumentChecksummer() != null),
                yn(cfg.isDocumentDeduplicate()
                        && cfg.getDocumentChecksummer() != null),
                scope));
    }

    private static String yn(boolean value) {
        return value ? "Yes" : "No";
    }
}
